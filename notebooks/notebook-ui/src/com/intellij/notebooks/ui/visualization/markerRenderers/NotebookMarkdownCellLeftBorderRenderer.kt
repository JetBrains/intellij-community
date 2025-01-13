// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Draws the left side of the markdown's cell border in the gutter area.
 */
class NotebookMarkdownCellLeftBorderRenderer(
  private val highlighter: RangeHighlighter,
  private val color: Color,
  private val boundsProvider: (Editor) -> Pair<Int, Int> = { editor ->
    val lines = IntRange(
      editor.document.getLineNumber(highlighter.startOffset),
      editor.document.getLineNumber(highlighter.endOffset)
    )
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val height = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight - top
    top to height
  }
) : NotebookLineMarkerRenderer() {

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val (top, height) = boundsProvider(editor)

    val appearance = editor.notebookAppearance
    val leftBorderX = r.x + r.width - appearance.getLeftBorderWidth()

    g.color = color
    g.drawLine(leftBorderX, top, leftBorderX, top + height)
  }
}