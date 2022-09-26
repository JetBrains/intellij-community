// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import java.awt.Graphics
import java.awt.Rectangle

abstract class NotebookLineMarkerRenderer : LineMarkerRendererEx {
  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM

  protected fun getInlayBounds(editor: EditorEx, linesRange: IntRange) : Rectangle? {
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineStartOffset(linesRange.last)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)

    val inlay = inlays.firstOrNull()
    return inlay?.bounds
  }

  protected fun getInlayBounds(editor: EditorEx, linesRange: IntRange, inlayId: Long) : Rectangle? {
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val inlays = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)

    val inlay = inlays.firstOrNull { it is RangeMarkerEx && it.id == inlayId }
    return inlay?.bounds
  }
}

class NotebookAboveCodeCellGutterLineMarkerRenderer(private val lines: IntRange, private val inlayId: Long) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val inlayBounds = getInlayBounds(editor, lines, inlayId) ?: return

    val bottomRectHeight = editor.notebookAppearance.CELL_BORDER_HEIGHT / 2
    paintNotebookCellBackgroundGutter(editor, g, r, lines, inlayBounds.y + inlayBounds.height - bottomRectHeight, bottomRectHeight)
  }
}