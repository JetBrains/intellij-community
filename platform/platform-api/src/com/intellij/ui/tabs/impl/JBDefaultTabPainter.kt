// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.rd.fill2DRect
import com.intellij.openapi.rd.fill2DRoundRect
import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.ColorUtil
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

  override fun getCustomBackground(tabColor: Color?, selected: Boolean, active: Boolean, hovered: Boolean): Color? {
    var bg: Color? = null
    if (!selected) {
      if (tabColor != null) {
        bg = theme.inactiveColoredTabBackground?.let { inactive ->
          ColorUtil.alphaBlending(inactive, tabColor)
        } ?: tabColor
      }

      if (hovered) {
        (if (active) theme.hoverBackground else theme.hoverInactiveBackground)?.let { hover ->
          bg = bg?.let { ColorUtil.alphaBlending(hover, it) } ?: hover
        }
      }
    }
    else {
      bg = (tabColor ?: if (active) theme.underlinedTabBackground else theme.underlinedTabInactiveBackground) ?: theme.background
      if (hovered) {
        (if (active) theme.hoverSelectedBackground else theme.hoverSelectedInactiveBackground).let { hover ->
          bg = bg?.let { ColorUtil.alphaBlending(hover, it) } ?: hover
        }
      }
    }
    return bg
  }

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    theme.background?.let{
      g.fill2DRect(rect, it)
    }
  }

  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    getCustomBackground(tabColor, selected = false, active, hovered)?.let {
      g.fill2DRect(rect, it)
    }
  }

  override fun paintSelectedTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, active: Boolean, hovered: Boolean) {
    getCustomBackground(tabColor, selected = true, active, hovered)?.let {
      g.fill2DRect(rect, it)
    }

    //this code smells. Remove when animation is default for all tabs
    if (!JBEditorTabsBorder.hasAnimation() || this !is JBEditorTabPainter) {
      paintUnderline(position, rect, borderThickness, g, active)
    }
  }

  override fun paintUnderline(position: JBTabsPosition,
                              rect: Rectangle,
                              borderThickness: Int,
                              g: Graphics2D,
                              active: Boolean) {
    val underline = underlineRectangle(position, rect, theme.underlineHeight)
    val arc = theme.underlineArc
    val color = if (active) theme.underlineColor else theme.inactiveUnderlineColor
    if (arc > 0) {
      g.fill2DRoundRect(underline, arc.toDouble(), color)
    } else {
      g.fill2DRect(underline, color)
    }
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
