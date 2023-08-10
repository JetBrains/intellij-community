package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.util.Consumer
import org.jetbrains.plugins.notebooks.ui.visualization.*
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle


class NotebookGutterLineMarkerManager {

  fun attachHighlighters(editor: EditorEx) {
    editor.addEditorDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) = putHighlighters(editor)
      override fun bulkUpdateFinished(document: Document) = putHighlighters(editor)
    })

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

    val notebookCellLines = NotebookCellLines.get(editor)

    for (interval in notebookCellLines.intervals) {
      val startOffset = editor.document.getLineStartOffset(interval.lines.first)
      val endOffset = editor.document.getLineEndOffset(interval.lines.last)
      editor.markupModel.addRangeHighlighter(
        null,
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,  // Border should be seen behind any syntax highlighting, selection or any other effect.
        HighlighterTargetArea.LINES_IN_RANGE
      ).also {
        it.lineMarkerRenderer = NotebookGutterLineMarkerRenderer(interval)
      }

      if (interval.type == NotebookCellLines.CellType.CODE && editor.notebookAppearance.shouldShowCellLineNumbers() && editor.editorKind != EditorKind.DIFF) {
        editor.markupModel.addRangeHighlighter(
          null,
          startOffset,
          endOffset,
          HighlighterLayer.FIRST - 99,  // Border should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterTargetArea.LINES_IN_RANGE
        ).also {
          it.lineMarkerRenderer = NotebookCellLineNumbersLineMarkerRenderer(it)
        }
      }

      if (interval.type == NotebookCellLines.CellType.CODE) {
        val changeAction = Consumer { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookCodeCellBackgroundLineMarkerRenderer(o)
        }
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false, changeAction)
      } else if (editor.editorKind != EditorKind.DIFF) {
        val changeAction = Consumer { o: RangeHighlighterEx ->
          o.lineMarkerRenderer = NotebookTextCellBackgroundLineMarkerRenderer(o)
        }
        editor.markupModel.addRangeHighlighterAndChangeAttributes(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE, false, changeAction)
      }

      val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")
      for (controller: NotebookCellInlayController in notebookCellInlayManager.inlaysForInterval(interval)) {
        controller.createGutterRendererLineMarker(editor, interval)
      }
    }
  }

  fun paintBackground(editor: EditorImpl,
                     g: Graphics,
                     r: Rectangle,
                     interval: NotebookCellLines.Interval) {
    val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")

    for (controller: NotebookCellInlayController in notebookCellInlayManager.inlaysForInterval(interval)) {
      controller.paintGutter(editor, g, r, interval)
    }
  }

  inner class NotebookGutterLineMarkerRenderer(private val interval: NotebookCellLines.Interval) : NotebookLineMarkerRenderer() {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      editor as EditorImpl

      @Suppress("NAME_SHADOWING")
      g.create().use { g ->
        g as Graphics2D

        val visualLineStart = editor.xyToVisualPosition(Point(0, g.clip.bounds.y)).line
        val visualLineEnd = editor.xyToVisualPosition(Point(0, g.clip.bounds.run { y + height })).line
        val logicalLineStart = editor.visualToLogicalPosition(VisualPosition(visualLineStart, 0)).line
        val logicalLineEnd = editor.visualToLogicalPosition(VisualPosition(visualLineEnd, 0)).line

        if (interval.lines.first > logicalLineEnd || interval.lines.last < logicalLineStart) return

        paintBackground(editor, g, r, interval)
      }
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





