// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.commits

import com.intellij.collaboration.ui.codereview.commits.CommitNodeComponent.Type.*
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.MacUIUtil
import com.intellij.vcs.log.paint.PaintParameters
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent

open class CommitNodeComponent : JComponent() {

  var type = SINGLE

  init {
    isOpaque = false
  }

  override fun getPreferredSize() = JBDimension(
    PaintParameters.getNodeWidth(PaintParameters.ROW_HEIGHT),
    PaintParameters.ROW_HEIGHT
  )

  override fun paintComponent(g: Graphics) {
    val rect = Rectangle(size)
    JBInsets.removeFrom(rect, insets)

    val g2 = g as Graphics2D

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)

    if (isOpaque) {
      g2.color = background
      g2.fill(Rectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(), rect.width.toFloat(), rect.height.toFloat()))
    }

    g2.color = foreground
    drawNode(g2, rect)
    if (type == LAST || type == MIDDLE) {
      drawEdgeUp(g2, rect)
    }
    if (type == FIRST || type == MIDDLE) {
      drawEdgeDown(g2, rect)
    }
  }

  private fun drawNode(g: Graphics2D, rect: Rectangle) {
    val radius = calcRadius(rect)
    val circle = Ellipse2D.Double(rect.centerX - radius, rect.centerY - radius, radius * 2.0, radius * 2.0)
    g.fill(circle)
  }

  protected open fun calcRadius(rect: Rectangle) = PaintParameters.getCircleRadius(rect.height)

  private fun drawEdgeUp(g: Graphics2D, rect: Rectangle) {
    val y1 = 0.0
    val y2 = rect.centerY
    drawEdge(g, rect, y1, y2)
  }

  private fun drawEdgeDown(g: Graphics2D, rect: Rectangle) {
    val y1 = rect.centerY
    val y2 = rect.maxY
    drawEdge(g, rect, y1, y2)
  }

  private fun drawEdge(g: Graphics2D, rect: Rectangle, y1: Double, y2: Double) {
    val x = rect.centerX
    val width = calcLineThickness(rect)
    val line = Rectangle2D.Double(x - width / 2, y1 - 0.5, width.toDouble(), y1 + y2 + 0.5)
    g.fill(line)
  }

  protected open fun calcLineThickness(rect: Rectangle) = PaintParameters.getLineThickness(rect.height)

  enum class Type {
    SINGLE, FIRST, MIDDLE, LAST
  }

  companion object {
    fun typeForListItem(itemIndex: Int, listSize: Int): Type = when {
      listSize <= 1 -> SINGLE
      itemIndex == 0 -> FIRST
      itemIndex == listSize - 1 -> LAST
      else -> MIDDLE
    }
  }
}