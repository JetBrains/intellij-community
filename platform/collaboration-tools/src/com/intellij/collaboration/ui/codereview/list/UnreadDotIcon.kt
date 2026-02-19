// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list

import com.intellij.ui.JBColor
import com.intellij.ui.NewUI
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import javax.swing.Icon

// Taken from com.jetbrains.rd.platform.codeWithMe.control.icons.CircleIcon
internal class UnreadDotIcon(
  private val unscaledDiameter: Int = DEFAULT_UNSCALED_DIAMETER,
) : Icon {
  companion object {
    const val DEFAULT_UNSCALED_DIAMETER = 6

    private val oldColor: Color = JBColor.namedColor("Review.Notification.Blue", 0x40B6E0)
    private val newColor: Color = JBColor.namedColor("Review.Notification.Blue", 0x3574F0, 0x548AF7)
  }

  private val color by lazy { if (NewUI.isEnabled()) newColor else oldColor }

  private val diameter
    get() = JBUI.scale(unscaledDiameter)

  override fun getIconHeight() = diameter

  override fun getIconWidth() = iconHeight

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val g2d = g.create() as Graphics2D

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val x1 = x
    val y1 = y

    val cx = x1.toFloat()
    val cy = y1.toFloat()
    val cd = diameter.toFloat()

    val circle = Ellipse2D.Float(cx, cy, cd, cd)
    g2d.color = color
    g2d.fill(circle)

    g2d.dispose()
  }
}