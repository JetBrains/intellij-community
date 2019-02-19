// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabTheme
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

open class JBDefaultTabPainter(val theme : TabTheme = TabTheme()) : JBTabPainter {

  override fun getBackgroundColor(): Color = theme.background ?: theme.borderColor

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    theme.background?.let{
      g.fillRect(rect, theme.background)
    }
  }

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, hovered: Boolean) {
    tabColor?.let {
      g.fillRect(rect, tabColor)
    }

    if(hovered) {
      g.fillRect(rect, if(tabColor != null) theme.hoverOverlayColor else theme.borderColor)
      return
    }

    tabColor ?: return
    g.fillRect(rect, theme.unselectedOverlayColor)
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean) {
    val color = tabColor ?: theme.background

    /**
     *  background filled for editors tab dragging
     */
    color?.let {
      g.fillRect(rect, color)
    }

    if(hovered) {
      g.fillRect(rect, if(tabColor != null) theme.hoverOverlayColor else theme.borderColor)
    }

    val underline = underlineRectangle(position, rect, theme.underlineThickness)
    g.fillRect(underline, if(active) theme.underline else theme.inactiveUnderline)
  }

  override fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point) {
    g.color = theme.borderColor

    /**
     * unexpected behaviour of {@link #LinePainter2D.paint(java.awt.Graphics2D, double, double, double, double, com.intellij.ui.paint.LinePainter2D.StrokeType, double)}
     */
    if (thickness == 1) {
      LinePainter2D.paint(g, from.getX(), from.getY(), to.getX(), to.getY())
      return
    }
    LinePainter2D.paint(g, from.getX(), from.getY(), to.getX(), to.getY(), LinePainter2D.StrokeType.INSIDE,
                        thickness.toDouble())
  }

  protected open fun underlineRectangle(position: JBTabsPosition,
                                        rect: Rectangle,
                                        thickness: Int): Rectangle {
    return Rectangle(rect.x, rect.y + rect.height - thickness, rect.width, thickness)
  }
}