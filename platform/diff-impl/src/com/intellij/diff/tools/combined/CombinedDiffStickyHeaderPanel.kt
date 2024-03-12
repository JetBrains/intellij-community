// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.border.LineBorder

internal class CombinedDiffStickyHeaderPanel(layout: LayoutManager?, private val arc: Int = 8) : JPanel(layout) {

  init {
    isOpaque = false
    border = MyShiftedBorder(CombinedDiffUI.EDITOR_BORDER_COLOR, arc + 2)
  }

  override fun setOpaque(isOpaque: Boolean) {} // Disable opaque

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAAPainting(g2)
      g2.color = background
      g2.fill(getShape())
      super.paintComponent(g)
    }
    finally {
      g2.dispose()
    }
  }

  private fun getShape(): Shape {
    val rect = Rectangle(size)
    JBInsets.removeFrom(rect, insets)
    return RoundRectangle2D.Float(rect.x.toFloat(), rect.y.toFloat(),
                                  rect.width.toFloat(), rect.height.toFloat() + arc.toFloat(),
                                  arc.toFloat(), arc.toFloat())

  }

  private class MyShiftedBorder(
    color: Color,
    private val arc: Int,
  ) : LineBorder(color, 1) {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val g2 = g as Graphics2D

      val oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val oldColor = g2.color
      g2.color = lineColor

      g2.drawRoundRect(x, y, width - 1, height + arc, arc, arc)
      g2.drawLine(x, height, width, height)

      g2.color = oldColor
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
    }
  }
}