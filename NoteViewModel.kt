package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.Note
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NoteRepository
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val isDarkMode = MutableStateFlow<Boolean?>(
        if (prefs.contains("is_dark_mode")) prefs.getBoolean("is_dark_mode", false) else null
    )

    fun toggleDarkMode(currentSystemDark: Boolean) {
        val current = isDarkMode.value ?: currentSystemDark
        val newValue = !current
        isDarkMode.value = newValue
        prefs.edit().putBoolean("is_dark_mode", newValue).apply()
    }

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("সব")

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
        populateSampleNotesIfEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<Note>> = combine(searchQuery, selectedCategory) { query, cat ->
        query to cat
    }.flatMapLatest { (query, cat) ->
        val baseFlow = if (query.isNotBlank()) {
            repository.searchNotes(query)
        } else {
            repository.allNotes
        }
        baseFlow.map { list ->
            if (cat == "সব") list else list.filter { it.tag == cat }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    // Current note being edited
    val currentNote = MutableStateFlow<Note?>(null)

    private fun populateSampleNotesIfEmpty() {
        viewModelScope.launch {
            try {
                val existingNotes = repository.allNotes.first()
                if (existingNotes.isEmpty()) {
                    val sample1 = Note(
                        title = "স্বাগতম - নোট ও ডকুমেন্ট এডিটর",
                        content = "আমাদের স্মার্ট নোট এডিটরে আপনাকে স্বাগতম!\n\nবৈশিষ্ট্যসমূহ:\n• বিভিন্ন ফন্টে (বাংলা, উর্দু, নাস্তালিক, আরবি, ইংরেজি) নোট তৈরি\n• উপরে-নিচে গাণিতিক অংক সাজানো (Vertical Math)\n• পরীক্ষার খাতার লাল মার্জিন ও ২-কলাম ভিউ\n• সরাসরি Word (.docx) ও PDF ফাইল হিসেবে সেভ ও শেয়ার",
                        colorHex = "#E0F2FE",
                        fontName = "SolaimanLipi",
                        fontSizeSp = 16,
                        isPinned = true,
                        tag = "সাধারণ"
                    )

                    val sample2 = Note(
                        title = "পদার্থবিজ্ঞান প্রশ্নপত্র - ২০২৬",
                        content = "১. (ক) পরিমাপের একক কাকে বলে?\n(খ) নিউটনের গতির দ্বিতীয় সূত্রটি ব্যাখ্যা করো।\n(গ) উপরোক্ত উদ্দীপক হতে ত্বরণ নির্ণয় করো।\n\nসমীকরণ: F = m × a",
                        colorHex = "#FFF4CE",
                        fontName = "Tiro Bangla",
                        fontSizeSp = 16,
                        marginType = "EXAM_BORDER",
                        isPinned = false,
                        tag = "পরীক্ষার প্রশ্ন"
                    )

                    val sample3 = Note(
                        title = "উপরে-নিচে গাণিতিক হিসাব",
                        content = "  ৭৫০\n+ ২৫০\n------\n ১০০০\n\n(উপরে-নিচে অংক বাটনে চাপ দিয়ে যেকোনো হিসাব সহজে তৈরি করুন)",
                        colorHex = "#E6F4EA",
                        fontName = "Roboto",
                        fontSizeSp = 18,
                        tag = "গণিত"
                    )

                    repository.saveNote(sample1)
                    repository.saveNote(sample2)
                    repository.saveNote(sample3)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startNewNote() {
        currentNote.value = Note(
            title = "",
            content = "",
            colorHex = "#FFFFFF",
            fontName = "Roboto",
            fontSizeSp = 16,
            tag = if (selectedCategory.value != "সব") selectedCategory.value else "সাধারণ"
        )
    }

    fun selectNote(note: Note) {
        currentNote.value = note
    }

    fun closeNoteDetail() {
        currentNote.value = null
    }

    fun updateCurrentNote(updatedNote: Note) {
        currentNote.value = updatedNote
    }

    fun saveCurrentNote(onSaved: (Long) -> Unit = {}) {
        val note = currentNote.value ?: return
        if (note.title.isBlank() && note.content.isBlank() && note.drawingDataJson == null) return

        viewModelScope.launch {
            if (note.id == 0L) {
                val newId = repository.saveNote(note)
                currentNote.value = note.copy(id = newId)
                onSaved(newId)
            } else {
                repository.updateNote(note)
                onSaved(note.id)
            }
        }
    }

    fun togglePin(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note.copy(isPinned = !note.isPinned))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            if (currentNote.value?.id == note.id) {
                currentNote.value = null
            }
        }
    }

    fun duplicateNote(note: Note) {
        viewModelScope.launch {
            val copy = note.copy(
                id = 0,
                title = "${note.title} (কপি)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.saveNote(copy)
        }
    }
}
