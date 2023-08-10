// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import java.awt.*
import java.awt.geom.Line2D
import javax.swing.JComponent
import javax.swing.plaf.ComponentUI

class VerticalRoundedLineComponent(val lineWidth: Int) : JComponent() {

  init {
    updateUI()
  }

  override fun updateUI() {
    setUI(VerticalRoundedLineUI())
  }

  private class VerticalRoundedLineUI : ComponentUI() {
    override fun installUI(c: JComponent) {
      c.isOpaque = false
    }

    override fun paint(g: Graphics, c: JComponent) {
      c as VerticalRoundedLineComponent
      with(g as Graphics2D) {
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        color = c.foreground

        val width = JBUIScale.scale(c.lineWidth)
        stroke = BasicStroke(width.toFloat() / 2 + 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL)

        val center = width / 2
        draw(Line2D.Float(center.toFloat(), center.toFloat(), center.toFloat(), (c.height - center).toFloat()))
      }
    }

    override fun getMinimumSize(c: JComponent): Dimension = getPreferredSize(c)

    override fun getPreferredSize(c: JComponent): Dimension {
      c as VerticalRoundedLineComponent
      return JBDimension(c.lineWidth, 0)
    }

    override fun getMaximumSize(c: JComponent): Dimension = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
  }
}