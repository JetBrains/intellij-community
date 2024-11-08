package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.isFoldingEnabledKey
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.NotebookUtil.paintCaretRow
import com.intellij.notebooks.ui.visualization.NotebookUtil.paintCellStripe
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Rectangle

class NotebookTextCellBackgroundLineMarkerRenderer(private val highlighter: RangeHighlighter) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl

    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top

    paintCaretRow(editor, g, lines)
    if (editor.getUserData(isFoldingEnabledKey) != true) {
      val appearance = editor.notebookAppearance
      appearance.getCellStripeColor(editor, lines)?.let {
        paintCellStripe(appearance, g, r, it, top, height, editor)
      }
    }
  }
}