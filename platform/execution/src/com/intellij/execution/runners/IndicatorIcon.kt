// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners

import com.intellij.ui.ColorUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import javax.swing.Icon

class IndicatorIcon(val base: Icon?, val emptyIconWidth: Int, val emptyIconHeight: Int, val color: Color): Icon {
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val iSize = JBUIScale.scale(4)
    val g2d = g.create() as Graphics2D
    try {
      GraphicsUtil.setupAAPainting(g2d)
      g2d.color = color
      val shape = Ellipse2D.Double((x + iconWidth - iSize).toDouble(), (y + iconHeight - iSize).toDouble(),
                                   iSize.toDouble(), iSize.toDouble())
      g2d.fill(shape)
      g2d.color = ColorUtil.withAlpha(Color.BLACK, .40)
      g2d.draw(shape)
    }
    finally {
      g2d.dispose()
    }
  }

  override fun getIconWidth(): Int = base?.iconWidth ?: emptyIconWidth

  override fun getIconHeight(): Int = base?.iconHeight ?: emptyIconHeight
}