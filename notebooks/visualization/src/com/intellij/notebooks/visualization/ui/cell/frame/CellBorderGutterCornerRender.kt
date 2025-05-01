// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.frame

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.notebooks.visualization.controllers.selfUpdate.common.NotebookCellSelfInlayController
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.*
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
class CellBorderGutterCornerRender(
  private val isAbove: Boolean,
  private val inlayController: NotebookCellSelfInlayController,
) : NotebookLineMarkerRenderer(null) {

  private val editor
    get() = inlayController.editorCell.editor

  private val cellFrameManager
    get() = inlayController.editorCell.cellFrameManager

  private val frameColor
    get() = cellFrameManager?.state?.get()?.color

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    editor as EditorImpl
    val inlayBounds = inlayController.inlay?.bounds ?: return
    val bottomRectHeight = editor.notebookAppearance.cellBorderHeight / 2

    if (isAbove) {
      val delimiterHeight = inlayBounds.height - bottomRectHeight
      val topPosition = inlayBounds.y + delimiterHeight
      paintNotebookCellBorderGutter(g, r, topPosition, bottomRectHeight)
    }
    else {
      paintNotebookCellBorderGutter(g, r, inlayBounds.y, inlayBounds.height)
    }
  }


  private fun paintNotebookCellBorderGutter(
    g: Graphics,
    r: Rectangle,
    top: Int,
    height: Int,
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

    g2d.color = frameColor
    g2d.stroke = BasicStroke(1.0f)

    if (isAbove) {
      g2d.draw(Line2D.Float(leftBorderX, topY, rightBorderX, topY))
      g2d.draw(Line2D.Float(leftBorderX, topY, leftBorderX, bottomY))
    }
    else {
      g2d.draw(Line2D.Float(leftBorderX, bottomY - 1, rightBorderX, bottomY - 1))
      g2d.draw(Line2D.Float(leftBorderX, topY, leftBorderX, bottomY - 1))
    }

    g2d.stroke = originalStroke
    g2d.setRenderingHints(originalHints)
  }
}