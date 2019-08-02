// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.rd.fill2DRect
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.themes.EditorTabTheme
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle

class JBEditorTabPainter : JBDefaultTabPainter(EditorTabTheme()) {
  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, hovered: Boolean) {
    when (position) {
      JBTabsPosition.top -> rect.height -= borderThickness
      JBTabsPosition.bottom -> rect.y += borderThickness
      JBTabsPosition.right -> {
        rect.x += borderThickness
      }
    }

    tabColor?.let {
      g.fill2DRect(rect, it)

      if(theme is EditorTabTheme)
      theme.inactiveColoredFileBackground?.let { inactive ->
        g.fill2DRect(rect, inactive)
      }
    }

    if(hovered) {
      g.fillRect(rect, theme.hoverBackground)
    }
  }

  override fun paintSelectedTab(position: JBTabsPosition,
                                g: Graphics2D,
                                rect: Rectangle,
                                borderThickness: Int,
                                tabColor: Color?,
                                active: Boolean,
                                hovered: Boolean) {

    val color = (tabColor ?: if(active) theme.underlinedTabBackground else theme.underlinedTabInactiveBackground) ?: theme.background

    color?.let {
      g.fill2DRect(rect, it)
    }

    if(hovered) {
      (if (active) theme.hoverBackground else theme.hoverInactiveBackground)?.let{
        g.fill2DRect(rect, it)
      }
    }
  }


  fun paintLeftGap(g: Graphics2D, rect: Rectangle, borderThickness: Int) {
    val maxY = rect.y + rect.height - borderThickness

    paintBorderLine(g, borderThickness, Point(rect.x, rect.y), Point(rect.x, maxY))
  }

  fun paintRightGap(g: Graphics2D, rect: Rectangle, borderThickness: Int) {
    val maxX = rect.x + rect.width - borderThickness
    val maxY = rect.y + rect.height - borderThickness

    paintBorderLine(g, borderThickness, Point(maxX, rect.y), Point(maxX, maxY))
  }

  override fun underlineRectangle(position: JBTabsPosition, rect: Rectangle, thickness: Int): Rectangle {
    return when (position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      JBTabsPosition.right -> Rectangle(rect.x, rect.y, thickness, rect.height)
      else -> super.underlineRectangle(position, rect, thickness)
    }
  }
}