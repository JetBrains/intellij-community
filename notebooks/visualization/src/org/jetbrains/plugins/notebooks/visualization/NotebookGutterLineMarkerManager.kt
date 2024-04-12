package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookLineMarkerRenderer


class NotebookGutterLineMarkerManager {

  fun attachHighlighters(editor: EditorEx) {
    NotebookIntervalPointerFactory.get(editor).changeListeners.addListener(object : NotebookIntervalPointerFactory.ChangeListener {
      override fun onUpdated(event: NotebookIntervalPointersEvent) {
        putHighlighters(editor)
      }
    }, (editor as EditorImpl).disposable)

    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        putHighlighters(editor)
      }
    })

    putHighlighters(editor)
  }

  fun putHighlighters(editor: EditorEx) {
    val highlighters = editor.markupModel.allHighlighters.filter { it.lineMarkerRenderer is NotebookLineMarkerRenderer }
    highlighters.forEach { editor.markupModel.removeHighlighter(it) }

    val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")
    for (cell in notebookCellInlayManager.cells) {
      cell.updateCellHighlight()
    }
  }

  companion object {
    fun install(editor: EditorEx): NotebookGutterLineMarkerManager {
      val instance = NotebookGutterLineMarkerManager()
      instance.attachHighlighters(editor)

      return instance
    }
  }
}





