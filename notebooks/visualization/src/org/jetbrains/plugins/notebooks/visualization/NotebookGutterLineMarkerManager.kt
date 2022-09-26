package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
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

      if (editor.settings.isLineNumbersShown && interval.type == NotebookCellLines.CellType.CODE && editor.notebookAppearance.shouldShowCellLineNumbers() && editor.editorKind != EditorKind.DIFF) {
        editor.markupModel.addRangeHighlighter(
          null,
          startOffset,
          endOffset,
          HighlighterLayer.FIRST - 99,  // Border should be seen behind any syntax highlighting, selection or any other effect.
          HighlighterTargetArea.LINES_IN_RANGE
        ).also {
          it.lineMarkerRenderer = NotebookCellLineNumbersLineMarkerRenderer(interval.lines)
        }
      }

      if (interval.type == NotebookCellLines.CellType.CODE) {
        editor.markupModel.addRangeHighlighter(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE).lineMarkerRenderer =
          NotebookCodeCellBackgroundLineMarkerRenderer(interval, interval.lines)
      } else if (editor.editorKind != EditorKind.DIFF) {
        editor.markupModel.addRangeHighlighter(null, startOffset, endOffset, HighlighterLayer.FIRST - 100, HighlighterTargetArea.LINES_IN_RANGE).lineMarkerRenderer =
          NotebookTextCellBackgroundLineMarkerRenderer(interval.lines)
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

class NotebookCellLineNumbersLineMarkerRenderer(private val lineRange: IntRange) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val visualLineStart = editor.xyToVisualPosition(Point(0, g.clip.bounds.y)).line
    val visualLineEnd = editor.xyToVisualPosition(Point(0, g.clip.bounds.run { y + height })).line
    val logicalLineStart = editor.visualToLogicalPosition(VisualPosition(visualLineStart, 0)).line
    val logicalLineEnd = editor.visualToLogicalPosition(VisualPosition(visualLineEnd, 0)).line

    g.font = editor.colorsScheme.getFont(EditorFontType.PLAIN).let {
      it.deriveFont(max(1f, it.size2D - 1f))
    }
    g.color = editor.colorsScheme.getColor(EditorColors.LINE_NUMBERS_COLOR)

    val notebookAppearance = editor.notebookAppearance
    var previousVisualLine = -1
    // The first line of the cell is the delimiter, don't draw the line number for it.
    for (logicalLine in max(logicalLineStart, lineRange.first + 1)..min(logicalLineEnd, lineRange.last)) {
      val visualLine = editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0)).line
      if (previousVisualLine == visualLine) continue  // If a region is folded, it draws only the first line number.
      previousVisualLine = visualLine

      if (visualLine < visualLineStart) continue
      if (visualLine > visualLineEnd) break

      // TODO conversions from document position to Y are expensive and should be cached.
      val yTop = editor.visualLineToY(visualLine)
      val lineNumber = logicalLine - lineRange.first
      val text: String = lineNumber.toString()
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

class NotebookCodeCellBackgroundLineMarkerRenderer(private val interval: NotebookCellLines.Interval, private val lineRange: IntRange) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl

    val top = editor.offsetToXY(editor.document.getLineStartOffset(lineRange.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lineRange.last)).y + editor.lineHeight - top

    paintNotebookCellBackgroundGutter(editor, g, r, interval.lines, top, height) {
      paintCaretRow(editor, g, lineRange)
    }
  }
}

class NotebookTextCellBackgroundLineMarkerRenderer(private val lineRange: IntRange) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl

    val top = editor.offsetToXY(editor.document.getLineStartOffset(lineRange.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lineRange.last)).y + editor.lineHeight - top

    paintCaretRow(editor, g, lineRange)
    val appearance = editor.notebookAppearance
    appearance.getCellStripeColor(editor, lineRange)?.let {
      paintCellStripe(appearance, g, r, it, top, height)
    }
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
