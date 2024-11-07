package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.paintCaretRow
import com.intellij.notebooks.ui.visualization.NotebookUtil.paintNotebookCellBackgroundGutter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Draws a vertical gray rectangle in the gutter
 * between the line numbers and the text.
 */
class NotebookCodeCellBackgroundLineMarkerRenderer(
  private val highlighter: RangeHighlighter,
  private val presentationModeMasking: Boolean = false,
) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintNotebookCellBackgroundGutter(editor, g, r, lines, top, height, presentationModeMasking) {
      paintCaretRow(editor, g, lines)
    }
  }
}