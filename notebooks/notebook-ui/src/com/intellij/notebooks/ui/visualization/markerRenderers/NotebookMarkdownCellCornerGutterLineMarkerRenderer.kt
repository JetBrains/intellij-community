// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

/**
 * Draws upper and lower left corners of the markdown cell border, see PY-74106 Improve the appearance of empty Markdown cells
 * If the position is top:
 *  _
 * |
 * And if bottom:
 * |
 * |_
 */
class NotebookMarkdownCellCornerGutterLineMarkerRenderer(
  private val highlighter: RangeHighlighter,
  private val position: Position,
  private val color: Color,
  inlayId: Long
) : NotebookLineMarkerRenderer(inlayId) {

  enum class Position { TOP, BOTTOM }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val lines = IntRange(
      editor.document.getLineNumber(highlighter.startOffset),
      editor.document.getLineNumber(highlighter.endOffset)
    )

    val inlayBounds = getInlayBounds(editor, lines) ?: return
    val bottomRectHeight = editor.notebookAppearance.cellBorderHeight / 2

    when (position) {
      Position.TOP -> {
        val delimiterHeight = inlayBounds.height - bottomRectHeight
        val topPosition = inlayBounds.y + delimiterHeight
        paintNotebookCellBorderGutter(
          editor, g, r, topPosition, bottomRectHeight, Position.TOP
        )
      }
      Position.BOTTOM -> {
        paintNotebookCellBorderGutter(
          editor, g, r, inlayBounds.y + inlayBounds.height - bottomRectHeight, bottomRectHeight, Position.BOTTOM
        )
      }
    }
  }

  fun paintNotebookCellBorderGutter(
    editor: EditorImpl,
    g: Graphics,
    r: Rectangle,
    top: Int,
    height: Int,
    position: Position,
  ) {
    val appearance = editor.notebookAppearance
    val borderWidth = appearance.getLeftBorderWidth()
    val leftBorderX = r.width - borderWidth
    val rightBorderX = r.x + r.width
    val topY = top
    val bottomY = top + height

    g.color = color

    when (position) {
      Position.TOP -> {
        g.drawLine(leftBorderX, topY, rightBorderX, topY)
        g.drawLine(leftBorderX, topY, leftBorderX, bottomY)
      }
      Position.BOTTOM -> {
        g.drawLine(leftBorderX, bottomY - 1, rightBorderX, bottomY - 1)
        g.drawLine(leftBorderX, topY, leftBorderX, bottomY - 1)
      }
    }
  }
}
