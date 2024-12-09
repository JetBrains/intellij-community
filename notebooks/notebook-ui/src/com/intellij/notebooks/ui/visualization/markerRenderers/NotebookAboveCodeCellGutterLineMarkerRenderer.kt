package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.NotebookUtil.paintNotebookCellBackgroundGutter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Graphics
import java.awt.Rectangle

class NotebookAboveCodeCellGutterLineMarkerRenderer(private val highlighter: RangeHighlighter, inlayId: Long) : NotebookLineMarkerRenderer(inlayId) {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val inlayBounds = getInlayBounds(editor, lines) ?: return

    val bottomRectHeight = editor.notebookAppearance.cellBorderHeight / 2
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y + inlayBounds.height - bottomRectHeight, bottomRectHeight)
  }
}
