// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl.tabPainters

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

interface JBTabPainter {
  companion object {
    val EDITOR_TAB = TabTheme(thickness = 3)
  }


  fun paintTab(g2d : Graphics2D, rect: Rectangle, tabColor: Color?)
  fun paintSelectedTab(g2d : Graphics2D, rect: Rectangle, tabColor: Color?, position : JBTabsPosition, active: Boolean)
}

class TabTheme(
  val background: Color? = null,
  val underline: Color = JBUI.CurrentTheme.DefaultTabs.underlineColor(),
  val inactiveUnderline: Color = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor(),
  val thickness : Int = 2
  )



class JBDefaultTabPainter(val theme : TabTheme = TabTheme()) : JBTabPainter {
  override fun paintTab(g2d: Graphics2D, rect: Rectangle, tabColor: Color?) {
    g2d.color = tabColor ?: return
    g2d.fillRect(rect.x, rect.y, rect.width, rect.height)
  }

  override fun paintSelectedTab(g2d: Graphics2D, rect: Rectangle, tabColor: Color?, position: JBTabsPosition, active: Boolean) {
    paintTab(g2d, rect, tabColor)

    val thickness = theme.thickness

    val underline = when(position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y - 1, rect.width, thickness)
      JBTabsPosition.top -> Rectangle(rect.x, rect.y + rect.height - thickness + 1, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness + 1, rect.y, thickness, rect.height)
      else -> Rectangle(rect.x, rect.y, thickness, rect.height)
    }

    g2d.color = if(active) theme.underline else theme.inactiveUnderline
    g2d.fillRect(underline.x, underline.y, underline.width, underline.height)
  }

}