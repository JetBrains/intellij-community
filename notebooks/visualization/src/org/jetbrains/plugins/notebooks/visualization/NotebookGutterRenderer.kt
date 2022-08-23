package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min


class NotebookGutterRenderer {

  fun attachHighlighter(editor: EditorEx) {
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

  private fun putHighlighters(editor: EditorEx) {
    val highlighters = editor.markupModel.allHighlighters.filter { it.lineMarkerRenderer is NotebookGutterLineMarker }
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
        it.lineMarkerRenderer = NotebookGutterLineMarker(interval)
      }
    }
  }

  fun paintBackground(editor: EditorImpl,
                     g: Graphics,
                     r: Rectangle,
                     interval: NotebookCellLines.Interval) {
    val notebookCellLines = NotebookCellLines.get(editor)
    val notebookCellInlayManager = NotebookCellInlayManager.get(editor) ?: throw AssertionError("Register inlay manager first")

    val top = editor.offsetToXY(editor.document.getLineStartOffset(interval.lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(interval.lines.last)).y + editor.lineHeight - top

    val appearance = editor.notebookAppearance
    if (interval.type == NotebookCellLines.CellType.CODE) {
      paintNotebookCellBackgroundGutter(editor, g, r, interval, top, height) {
        paintCaretRow(editor, g, interval.lines)
      }
    }
    else {
      if (editor.editorKind == EditorKind.DIFF) return
      paintCaretRow(editor, g, interval.lines)
      appearance.getCellStripeColor(editor, interval)?.let {
        appearance.paintCellStripe(g, r, it, top, height)
      }
    }

    for (controller: NotebookCellInlayController in notebookCellInlayManager.inlaysForInterval(interval)) {
      controller.paintGutter(editor, g, r, notebookCellLines.intervals.listIterator(interval.ordinal))
    }
  }

  private fun paintCaretRow(editor: EditorImpl, g: Graphics, lines: IntRange) {
    if (editor.settings.isCaretRowShown) {
      val caretModel = editor.caretModel
      val caretLine = caretModel.logicalPosition.line
      if (caretLine in lines) {
        g.color = editor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
        g.fillRect(
          0,
          editor.visualLineToY(caretModel.visualPosition.line),
          g.clipBounds.width,
          editor.lineHeight
        )
      }
    }
  }
  fun paintLineNumbers(editor: EditorImpl,
                     g: Graphics,
                     r: Rectangle,
                       interval: NotebookCellLines.Interval,
                     visualLineStart: Int,
                     visualLineEnd: Int,
                     logicalLineStart: Int,
                     logicalLineEnd: Int) {
    if (!editor.notebookAppearance.shouldShowCellLineNumbers()) {
      return
    }
    if (editor.editorKind == EditorKind.DIFF) return

    if (editor.settings.isLineNumbersShown && interval.type == NotebookCellLines.CellType.CODE) {
      g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN).let {
        it.deriveFont(max(1f, it.size2D - 1f))
      }
      g.color = editor.colorsScheme.getColor(EditorColors.LINE_NUMBERS_COLOR)

      val notebookAppearance = editor.notebookAppearance
      var previousVisualLine = -1
      // The first line of the cell is the delimiter, don't draw the line number for it.
      for (logicalLine in max(logicalLineStart, interval.lines.first + 1)..min(logicalLineEnd, interval.lines.last)) {
        val visualLine = editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0)).line
        if (previousVisualLine == visualLine) continue  // If a region is folded, it draws only the first line number.
        previousVisualLine = visualLine

        if (visualLine < visualLineStart) continue
        if (visualLine > visualLineEnd) break

        // TODO conversions from document position to Y are expensive and should be cached.
        val yTop = editor.visualLineToY(visualLine)
        val lineNumber = logicalLine - interval.lines.first
        val text = lineNumber.toString()
        val left =
          (
            r.width
            - FontLayoutService.getInstance().stringWidth(g.fontMetrics, text)
            - notebookAppearance.LINE_NUMBERS_MARGIN
            - notebookAppearance.getLeftBorderWidth()
          )
        g.drawString(text, left, yTop + editor.ascent)
      }
    }
  }



  inner class NotebookGutterLineMarker(private val interval: NotebookCellLines.Interval) : LineMarkerRendererEx {
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
        paintLineNumbers(editor, g, r, interval, visualLineStart, visualLineEnd, logicalLineStart, logicalLineEnd)
      }
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM
  }

  companion object {
    fun install(editor: EditorEx): NotebookGutterRenderer {
      val instance = NotebookGutterRenderer()
      instance.attachHighlighter(editor)

      return instance
    }
  }
}