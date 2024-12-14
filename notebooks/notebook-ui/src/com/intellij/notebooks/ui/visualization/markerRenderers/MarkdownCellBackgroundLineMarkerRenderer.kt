// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.JBColor
import java.awt.Graphics
import java.awt.Rectangle

class MarkdownCellBackgroundLineMarkerRenderer(
  private val highlighter: RangeHighlighter
) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(editor.document.getLineNumber(highlighter.startOffset), editor.document.getLineNumber(highlighter.endOffset))
    val top = editor.offsetToXY(editor.document.getLineStartOffset(lines.first)).y
    val bottom = editor.offsetToXY(editor.document.getLineEndOffset(lines.last)).y + editor.lineHeight

    val appearance = editor.notebookAppearance
    val x = r.x + r.width - appearance.getLeftBorderWidth()

    // Drawing a vertical 1px line
    g.color = JBColor.RED
    g.drawLine(x, top, x, bottom)
  }
}