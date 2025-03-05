// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization.markerRenderers

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Line2D

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

  enum class Position { BOTTOM, MIDDLE, TOP }

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
      Position.MIDDLE, Position.BOTTOM -> {
        paintNotebookCellBorderGutter(
          editor, g, r, inlayBounds.y, inlayBounds.height, position
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
    val g2d = g as Graphics2D
    val appearance = editor.notebookAppearance
    val borderWidth = appearance.getLeftBorderWidth()
    val leftBorderX = (r.width - borderWidth).toFloat()
    val rightBorderX = (r.x + r.width).toFloat()
    val topY = top.toFloat() + 0.5f
    val bottomY = (top + height).toFloat() + 0.5f

    val originalStroke = g2d.stroke
    val originalHints = g2d.renderingHints

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g2d.color = color
    g2d.stroke = BasicStroke(1.0f)

    when (position) {
      Position.TOP -> {
        g2d.draw(Line2D.Float(leftBorderX, topY, rightBorderX, topY))
        g2d.draw(Line2D.Float(leftBorderX, topY, leftBorderX, bottomY))
      }
      Position.MIDDLE -> {
        g2d.draw(Line2D.Float(leftBorderX, topY, leftBorderX, bottomY - 1))
      }
      Position.BOTTOM -> {
        g2d.draw(Line2D.Float(leftBorderX, bottomY - 1, rightBorderX, bottomY - 1))
        g2d.draw(Line2D.Float(leftBorderX, topY, leftBorderX, bottomY - 1))
      }
    }

    g2d.stroke = originalStroke
    g2d.setRenderingHints(originalHints)
  }
}
