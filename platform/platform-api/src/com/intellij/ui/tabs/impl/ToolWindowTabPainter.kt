// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.impl.themes.ToolWindowTabTheme
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

class ToolWindowTabPainter: JBDefaultTabPainter(ToolWindowTabTheme())  {
  override fun paintTab(position: JBTabsPosition,
                        g: Graphics2D,
                        rect: Rectangle,
                        borderThickness: Int,
                        tabColor: Color?,
                        active: Boolean,
                        hovered: Boolean) {
    rect.y += borderThickness
    rect.height -= borderThickness

    when (position) {
      JBTabsPosition.top -> rect.height -= borderThickness
    }

    super.paintTab(position, g, rect, borderThickness, tabColor, active, hovered)
  }

  override fun paintSelectedTab(position: JBTabsPosition,
                                g: Graphics2D,
                                rect: Rectangle,
                                borderThickness: Int,
                                tabColor: Color?,
                                active: Boolean,
                                hovered: Boolean) {
    rect.y += borderThickness
    rect.height -= borderThickness

    super.paintSelectedTab(position, g, rect, borderThickness, tabColor, active, hovered)
  }
}