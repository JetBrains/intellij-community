// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

class JBEditorTabPainter(theme: TabTheme = TabTheme.EDITOR_TAB) : JBDefaultTabPainter(theme) {
  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, tabColor: Color?, hovered: Boolean) {
    val offset = theme.borderThickness

    when (position) {
      JBTabsPosition.top -> rect.height -= offset
      JBTabsPosition.bottom -> rect.y += offset
      JBTabsPosition.left -> rect.width += offset
      JBTabsPosition.right -> rect.x += offset
    }
    super.paintTab(position, g, rect, tabColor, hovered)
  }

  override fun underlineRectangle(position: JBTabsPosition, rect: Rectangle, thickness: Int): Rectangle {
    return when (position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      JBTabsPosition.right -> Rectangle(rect.x, rect.y, thickness, rect.height)
      else -> super.underlineRectangle(position, rect, thickness)
    }
  }

  override fun paintBorders(position: JBTabsPosition, g: Graphics2D, bounds: Rectangle, headerFitHeight: Int, rows: Int, yOffset: Int) {
    g.color = theme.borderColor
    when (position) {
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