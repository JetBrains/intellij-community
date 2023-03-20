// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.FontLayoutService
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.max
import kotlin.math.min

abstract class NotebookLineMarkerRenderer(private val inlayId: Long? = null) : LineMarkerRendererEx {
  fun getInlayId(): Long? = inlayId

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

  protected fun getInlayBounds(editor: EditorEx, linesRange: IntRange) : Rectangle? {
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)

    val inlay = inlays.firstOrNull { it is RangeMarkerEx && it.id == inlayId }
    return inlay?.bounds
  }
}

class NotebookAboveCodeCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return

    val bottomRectHeight = editor.notebookAppearance.CELL_BORDER_HEIGHT / 2
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y + inlayBounds.height - bottomRectHeight, bottomRectHeight)
  }
}

class NotebookBelowCellCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter,
                                                    inlayId: Long,
                                                    private val customHeight: Int) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y, customHeight)
  }
}

class MarkdownCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return
    paintCellGutter(inlayBounds, lines, editor, g, r)
  }
}

class NotebookCellLineNumbersLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    if (!editor.settings.isLineNumbersShown) return

    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
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
    for (logicalLine in max(logicalLineStart, lines.first + 1)..min(logicalLineEnd, lines.last)) {
      val visualLine = editor.logicalToVisualPosition(LogicalPosition(logicalLine, 0)).line
      if (previousVisualLine == visualLine) continue  // If a region is folded, it draws only the first line number.
      previousVisualLine = visualLine

      if (visualLine < visualLineStart) continue
      if (visualLine > visualLineEnd) break

      // TODO conversions from document position to Y are expensive and should be cached.
      val yTop = editor.visualLineToY(visualLine)
      val lineNumber = logicalLine - lines.first
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

class NotebookCodeCellBackgroundLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintNotebookCellBackgroundGutter(editor, g, r, lines, top, height) {
      paintCaretRow(editor, g, lines)
    }
  }
}

class NotebookTextCellBackgroundLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl

    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintCaretRow(editor, g, lines)
    val appearance = editor.notebookAppearance
    appearance.getCellStripeColor(editor, lines)?.let {
      paintCellStripe(appearance, g, r, it, top, height)
    }
  }
}

class NotebookCellToolbarGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(
  inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y, inlayBounds.height)
  }
}