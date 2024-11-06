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

class NotebookEditorModeListenerAdapter private constructor(private val editor: Editor) : NotebookEditorModeListener, CaretListener, Disposable.Default {
  private var currentEditorMode: NotebookEditorMode? = null


  override fun onModeChange(editor: Editor, mode: NotebookEditorMode) {
    val modeWasChanged = currentEditorMode != mode

    currentEditorMode = mode

    if (editor.isDisposed) {
      thisLogger().warn("Cannot change notebook mode, Editor is disposed already")
      return
    }

    editor.apply {
      (markupModel as MarkupModelEx).apply {
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

      caretModel.allCarets.forEach { caret ->
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
  }

  override fun caretAdded(event: CaretEvent) {
    val mode = currentEditorMode ?: return
    event.caret?.visualAttributes = getCaretAttributes(mode)
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

  private fun handleCarets(mode: NotebookEditorMode) = when (mode) {
    NotebookEditorMode.EDIT -> {
      // selection of multiple cells leads to multiple invisible carets, remove them
      editor.caretModel.removeSecondaryCarets()
    }
    NotebookEditorMode.COMMAND -> {
      // text selection shouldn't be visible in command mode
      for (caret in editor.caretModel.allCarets) {
        caret.removeSelection()
      }
    }
  }

  companion object {
    fun setupForEditor(editor: Editor) {
      val listener = NotebookEditorModeListenerAdapter(editor)
      editor.caretModel.addCaretListener(listener, listener)
      EditorUtil.disposeWithEditor(editor, listener)
      val connection = editor.project?.messageBus?.connect(listener)
      connection?.subscribe(NOTEBOOK_EDITOR_MODE, listener)
      listener.onModeChange(editor, editor.currentMode)
    }
  }
}