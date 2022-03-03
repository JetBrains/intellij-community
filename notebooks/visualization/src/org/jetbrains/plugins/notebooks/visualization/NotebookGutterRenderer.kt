package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle


class NotebookGutterRenderer {
  private var currentHighlighter: RangeHighlighter? = null

  fun reattachHighlighter(editor: EditorEx) {
    currentHighlighter?.let(editor.markupModel::removeHighlighter)
    currentHighlighter = editor.markupModel.addRangeHighlighter(
      null,
      0,
      editor.document.textLength,
      HighlighterLayer.FIRST - 100,  // Border should be seen behind any syntax highlighting, selection or any other effect.
      HighlighterTargetArea.LINES_IN_RANGE
    ).also {
      it.lineMarkerRenderer = lineMarkerRenderer
    }
  }

  private val lineMarkerRenderer = object : LineMarkerRendererEx {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      editor as EditorImpl

      @Suppress("NAME_SHADOWING")
      g.create().use { g ->
        g as Graphics2D

        val visualLineStart = editor.xyToVisualPosition(Point(0, g.clip.bounds.y)).line
        val visualLineEnd = editor.xyToVisualPosition(Point(0, g.clip.bounds.run { y + height })).line
        val logicalLineStart = editor.visualToLogicalPosition(VisualPosition(visualLineStart, 0)).line
        val logicalLineEnd = editor.visualToLogicalPosition(VisualPosition(visualLineEnd, 0)).line

        val gutterControllers = NotebookCellGutterController.EP_NAME.extensionList
        val notebookCellLines = NotebookCellLines.get(editor)
        for (interval in notebookCellLines.intervalsIterator(logicalLineStart)) {
          if (interval.lines.first > logicalLineEnd) break
          for (controller in gutterControllers) {
            controller.paint(
              editor,
              g,
              r,
              notebookCellLines.intervals.listIterator(interval.ordinal),
              visualLineStart,
              visualLineEnd,
              logicalLineStart,
              logicalLineEnd
            )
          }
        }
      }
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM
  }

  companion object {
    fun install(editor: EditorEx): NotebookGutterRenderer {
      val instance = NotebookGutterRenderer()
      instance.reattachHighlighter(editor)

      editor.addEditorDocumentListener(object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) = onChange(event.document)
        override fun bulkUpdateFinished(document: Document) = onChange(document)

        private fun onChange(document: Document) {
          if ((instance.currentHighlighter?.endOffset ?: Int.MAX_VALUE) < document.textLength) {
            instance.reattachHighlighter(editor)
          }
        }
      })

      editor.caretModel.addCaretListener(object : CaretListener {
        private var previousLines: IntRange? = null

        override fun caretPositionChanged(event: CaretEvent) {
          val caret = event.caret.takeIf { it === editor.caretModel.currentCaret } ?: return
          val line = caret.logicalPosition.line
          val actualLines = NotebookCellLines.get(editor)
            .intervalsIterator(line)
            .asSequence()
            .firstOrNull()
            ?.lines
            ?.takeIf { line in it }
          val reattach =
            (previousLines
               ?.let { actualLines == null || !(actualLines hasIntersectionWith it) }
             ?: actualLines) != null
          if (reattach) {
            previousLines = actualLines
            instance.reattachHighlighter(editor)
          }
        }
      })

      return instance
    }
  }
}