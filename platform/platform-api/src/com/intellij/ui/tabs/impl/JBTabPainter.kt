// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabTheme
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

class JBDefaultTabPainter(val theme : TabTheme = TabTheme()) : JBTabPainter {

  override fun getBackgroundColor(): Color = theme.background ?: theme.borderColor

  override fun getBorderThickness(): Int = theme.borderThickness

  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = theme.background
    g.fillRect(rect)
  }

  override fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, hovered: Boolean) {
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

  override fun paintSelectedTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, position: JBTabsPosition, active: Boolean, hovered: Boolean) {
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

    val underline = when(position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y, rect.width, thickness)
      JBTabsPosition.top -> Rectangle(rect.x, rect.y + rect.height - thickness, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      else -> Rectangle(rect.x, rect.y, thickness, rect.height)
    }

    g.color = if(active) theme.underline else theme.inactiveUnderline
    g.fillRect(underline)
  }

  override fun paintBorders(g: Graphics2D, bounds: Rectangle, position: JBTabsPosition, headerFitHeight: Int, rows: Int, yOffset: Int) {
    g.color = theme.borderColor
    when(position) {
      JBTabsPosition.top -> paintBorder(g, bounds, headerFitHeight, rows, 0)
      JBTabsPosition.bottom -> paintBorder(g, bounds, headerFitHeight, rows - 1, yOffset)
    }
  }

  private fun paintBorder(g: Graphics2D, bounds: Rectangle, headerFitHeight: Int, rows: Int, yOffset: Int) {
    for (eachRow in 0..rows) {
      val yl = (eachRow * headerFitHeight) + yOffset
      LinePainter2D.paint(g, bounds.x.toDouble(), yl.toDouble(), bounds.width.toDouble(), yl.toDouble())
    }
  }
}