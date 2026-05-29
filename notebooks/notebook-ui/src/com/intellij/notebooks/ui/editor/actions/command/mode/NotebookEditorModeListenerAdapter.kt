package com.intellij.notebooks.ui.editor.actions.command.mode

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Key
import com.intellij.ui.Gray

class NotebookEditorModeListenerAdapter private constructor(private val editor: Editor) : NotebookEditorModeListener, CaretListener, Disposable.Default {
  private var currentEditorMode: NotebookEditorMode? = null
  private var isInitializing = true

  override fun onModeChange(editor: Editor, mode: NotebookEditorMode) {
    val modeWasChanged = currentEditorMode != mode

    currentEditorMode = mode

    if (editor.isDisposed) {
      thisLogger().warn("Cannot change notebook mode, Editor is disposed already")
      return
    }

    (editor.markupModel as MarkupModelEx).apply {
      allHighlighters.filterIsInstance<RangeHighlighterEx>().forEach {
        val lineMarkerRenderer = it.getLineMarkerRenderer()
        it.setLineMarkerRenderer(null)
        it.setLineMarkerRenderer(lineMarkerRenderer) // to fireChange
      }
    }

    if (modeWasChanged) {
      handleCarets(mode)
      editor.settings.isCaretRowShown = isCaretRowShown(mode)
    }

    editor.caretModel.allCarets.forEach { caret ->
      caret.visualAttributes = getCaretAttributes(mode)
    }

    editor.contentComponent.putClientProperty(ActionUtil.ALLOW_PlAIN_LETTER_SHORTCUTS, when (mode) {
      NotebookEditorMode.EDIT -> false
      NotebookEditorMode.COMMAND -> true
    })

    editor.contentComponent.enableInputMethods(when (mode) {
                                                 NotebookEditorMode.EDIT -> true
                                                 NotebookEditorMode.COMMAND -> false
                                               })
  }

  override fun caretAdded(event: CaretEvent) {
    val mode = currentEditorMode ?: return
    event.caret.visualAttributes = getCaretAttributes(mode)
    (editor as EditorEx).gutterComponentEx.repaint()
  }

  override fun caretRemoved(event: CaretEvent) {
    (editor as EditorEx).gutterComponentEx.repaint()
  }

  private fun getCaretAttributes(mode: NotebookEditorMode) = when (mode) {
    NotebookEditorMode.EDIT -> CaretVisualAttributes.getDefault()
    NotebookEditorMode.COMMAND -> INVISIBLE_CARET
  }

  private fun isCaretRowShown(mode: NotebookEditorMode): Boolean = when (mode) {
    NotebookEditorMode.EDIT -> true
    NotebookEditorMode.COMMAND -> false
  }

  private fun handleCarets(mode: NotebookEditorMode) {
    if (isInitializing) return
    if (mode == NotebookEditorMode.EDIT) {
      // selection of multiple cells leads to multiple invisible carets, remove them
      editor.caretModel.removeSecondaryCarets()
      restoreSavedCaretPositions()
    }
    else {
      saveCaretPositions()
      // text selection shouldn't be visible in command mode
      for (caret in editor.caretModel.allCarets) {
        caret.removeSelection()
      }
    }
  }

  private fun saveCaretPositions() {
    val caretTracker = NotebookCellCaretTracker.getInstance() ?: return
    val caretSnapshot = caretTracker.saveCaretPositions(editor) ?: return
    val allSavedPositions = LinkedHashMap(editor.getUserData(SAVED_CARET_POSITIONS_KEY).orEmpty())
    allSavedPositions.remove(caretSnapshot.cellId)
    allSavedPositions[caretSnapshot.cellId] = caretSnapshot
    editor.putUserData(SAVED_CARET_POSITIONS_KEY, allSavedPositions)
  }

  private fun restoreSavedCaretPositions() {
    val caretTracker = NotebookCellCaretTracker.getInstance() ?: return
    val currentCellId = caretTracker.getCurrentCellId(editor) ?: return
    val allSavedPositions = LinkedHashMap(editor.getUserData(SAVED_CARET_POSITIONS_KEY).orEmpty())
    val savedSnapshot = allSavedPositions.remove(currentCellId) ?: return
    if (!caretTracker.restoreCaretPositions(editor, savedSnapshot.positions)) {
      allSavedPositions[savedSnapshot.cellId] = savedSnapshot
    }
    editor.putUserData(SAVED_CARET_POSITIONS_KEY, allSavedPositions.ifEmpty { null })
  }

  companion object {
    private val INVISIBLE_CARET = CaretVisualAttributes(Gray.TRANSPARENT, CaretVisualAttributes.Weight.NORMAL)
    private val SAVED_CARET_POSITIONS_KEY = Key.create<Map<NotebookCellCaretTracker.CellId, NotebookCellCaretTracker.CellCaretSnapshot>>("NOTEBOOK_SAVED_CARET_POSITIONS")

    fun setupForEditor(editor: Editor) {
      val listener = NotebookEditorModeListenerAdapter(editor)
      editor.caretModel.addCaretListener(listener, listener)
      EditorUtil.disposeWithEditor(editor, listener)
      val connection = editor.project?.messageBus?.connect(listener)
      connection?.subscribe(NOTEBOOK_EDITOR_MODE, listener)
      listener.onModeChange(editor, editor.currentMode)
      listener.isInitializing = false
    }
  }
}