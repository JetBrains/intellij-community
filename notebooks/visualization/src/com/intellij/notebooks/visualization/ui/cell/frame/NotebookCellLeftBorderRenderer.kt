// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.frame

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.openapi.editor.Editor
import java.awt.*
import java.awt.geom.Line2D

/**
 * Draws the left side of the markdown's cell border in the gutter area.
 */
class NotebookCellLeftBorderRenderer(
  private val frameRender: EditorCellFrameManager,
) : NotebookLineMarkerRenderer() {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val frameState = frameRender.state.get()
    if (frameState.isVisible.not())
      return
    val color = frameState.color

    val line2D = frameRender.calculateLineFrameVerticalLine() ?: return
    val top = line2D.y1
    val height = line2D.y2 - line2D.y1

    val g2d = g as Graphics2D

    val appearance = editor.notebookAppearance
    val leftBorderX = r.x + r.width - appearance.getLeftBorderWidth()

    val originalStroke = g2d.stroke
    val originalHints = g2d.renderingHints

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g2d.color = color
    g2d.stroke = BasicStroke(1.0f)

    g2d.draw(Line2D.Float(leftBorderX.toFloat(), top.toFloat(), leftBorderX.toFloat(), (top + height).toFloat()))

    g2d.stroke = originalStroke
    g2d.setRenderingHints(originalHints)
  }
}