// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.newImpl

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

class JBEditorTabPainter(theme: TabTheme = TabTheme.EDITOR) : JBDefaultTabPainter(theme) {
  override fun paintTab(position: JBTabsPosition, g: Graphics2D, rect: Rectangle, borderThickness: Int, tabColor: Color?, hovered: Boolean) {
    when (position) {
      JBTabsPosition.top -> rect.height -= borderThickness
      JBTabsPosition.bottom -> rect.y += borderThickness
     /* JBTabsPosition.left -> rect.width -= borderThickness*/
      JBTabsPosition.right -> {
        rect.x += borderThickness
      }
    }
    super.paintTab(position, g, rect, borderThickness, tabColor, hovered)
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