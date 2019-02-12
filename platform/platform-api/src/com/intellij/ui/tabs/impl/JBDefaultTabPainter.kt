// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabTheme
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

open class JBDefaultTabPainter(val theme : TabTheme = TabTheme.DEFAULT_TAB) : JBTabPainter {

  override fun getBackgroundColor(): Color = theme.background ?: theme.borderColor

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = theme.background
    g.fillRect(rect)
  }

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, hovered: Boolean) {
    tabColor?.let {
      g.color = tabColor
      g.fillRect(rect)
    }

    if(hovered) {
      g.color = theme.hoverOverlayColor
      g.fillRect(rect)
      return
    }

    tabColor ?: return
    g.color = theme.unselectedOverlayColor
    g.fillRect(rect)
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean) {
    val color = tabColor ?: theme.background

    /**
     *  background filled for editors tab dragging
     */
    color?.let {
      g.color = color
      g.fillRect(rect)
    }

    if(hovered) {
      g.color = theme.hoverOverlayColor
      g.fillRect(rect)
    }

    val thickness = theme.underlineThickness

    val underline = underlineRectangle(position, rect, thickness)

    // TODO use LinePainter2D.paint
    g.color = if(active) theme.underline else theme.inactiveUnderline
    g.fillRect(underline)
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