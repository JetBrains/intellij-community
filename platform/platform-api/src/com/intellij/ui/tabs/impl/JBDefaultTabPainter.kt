// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.rd.fill2DRect
import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.themes.DefaultTabTheme
import com.intellij.ui.tabs.impl.themes.TabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

open class JBDefaultTabPainter(val theme : TabTheme = DefaultTabTheme()) : JBTabPainter {

  override fun getTabTheme(): TabTheme = theme

  override fun getBackgroundColor(): Color = theme.background ?: theme.borderColor

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    theme.background?.let{
      g.fill2DRect(rect, it)
    }
  }

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    tabColor?.let {
      g.fill2DRect(rect, it)

      theme.inactiveColoredTabBackground?.let { inactive ->
        g.fill2DRect(rect, inactive)
      }
    }

    if(hovered) {
      (if (active) theme.hoverBackground else theme.hoverInactiveBackground)?.let{
        g.fill2DRect(rect, it)
      }
    }
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    val color = (tabColor ?: if(active) theme.underlinedTabBackground else theme.underlinedTabInactiveBackground) ?: theme.background

    color?.let {
      g.fill2DRect(rect, it)
    }

    if(hovered) {
      (if (active) theme.hoverBackground else theme.hoverInactiveBackground)?.let{
        g.fill2DRect(rect, it)
      }
    }

    paintUnderline(position, rect, borderThickness, g, active)
  }

  override fun paintUnderline(position: JBTabsPosition,
                              rect: Rectangle,
                              borderThickness: Int,
                              g: Graphics2D,
                              active: Boolean) {
    val underline = underlineRectangle(position, rect, theme.underlineHeight)
    g.fill2DRect(underline, if (active) theme.underlineColor else theme.inactiveUnderlineColor)
  }

  override fun paintBorderLine(g: Graphics2D, thickness: Int, from: Point, to: Point) {
    g.paint2DLine(from, to, LinePainter2D.StrokeType.INSIDE, thickness.toDouble(), theme.borderColor)
  }

  protected open fun underlineRectangle(position: JBTabsPosition,
                                        rect: Rectangle,
                                        thickness: Int): Rectangle {
    return Rectangle(rect.x, rect.y + rect.height - thickness, rect.width, thickness)
  }
}
