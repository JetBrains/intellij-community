// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.openapi.rd.fill2DRect
import com.intellij.openapi.rd.paint2DLine
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
      g.fill2DRect(rect, theme.background)
    }
  }

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, hovered: Boolean) {
    tabColor?.let {
      g.fill2DRect(rect, tabColor)
      theme.inactiveColoredFileBackground?.let {
        g.fill2DRect(rect, it)
      }
    }

    if(hovered) {
      g.fillRect(rect, theme.hoverBackground)
      return
    }
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, tabColor: Color?, active: Boolean, hovered: Boolean) {
    val color = tabColor ?: theme.underlinedTabBackground

    color?.let {
      g.fill2DRect(rect, color)
    }

    if(hovered) {
      g.fill2DRect(rect, theme.hoverBackground)
    }

    val underline = underlineRectangle(position, rect, theme.underlineHeight)
    g.fill2DRect(underline, if(active) theme.underlineColor else theme.inactiveUnderlineColor)
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
