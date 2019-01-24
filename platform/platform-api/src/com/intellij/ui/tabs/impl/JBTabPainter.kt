// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

interface JBTabPainter {
  companion object {
    val EDITOR_TAB = TabTheme(thickness = 3)
  }

  fun fillBackground(g : Graphics2D, rect : Rectangle)
  fun fillBeforeAfterTabs(g : Graphics2D, before : Rectangle, after : Rectangle)
  fun paintTab(g : Graphics2D, rect: Rectangle, tabColor: Color?)
  fun paintSelectedTab(g : Graphics2D, rect: Rectangle, tabColor: Color?, position : JBTabsPosition, active: Boolean)
}

class TabTheme(
  val background: Color = JBUI.CurrentTheme.EditorTabs.backgroundColor(),
  val defaultTabColor: Color = JBUI.CurrentTheme.EditorTabs.defaultTabColor(),
  val underline: Color = JBUI.CurrentTheme.DefaultTabs.underlineColor(),
  val inactiveUnderline: Color = JBUI.CurrentTheme.DefaultTabs.inactiveUnderlineColor(),
  val thickness : Int = 2
)

class JBDefaultTabPainter(val theme : TabTheme = TabTheme()) : JBTabPainter {
  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = theme.background
    g.fill(rect)
  }

  override fun fillBeforeAfterTabs(g: Graphics2D, before: Rectangle, after: Rectangle) {
    g.color = theme.defaultTabColor
    g.fillRect(before.x, before.y, before.width, before.height)
    g.fillRect(after.x, after.y, after.width, after.height)
  }

  override fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?) {
    g.color = tabColor ?: theme.defaultTabColor
    g.fillRect(rect.x, rect.y, rect.width, rect.height)
  }

  override fun paintSelectedTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, position: JBTabsPosition, active: Boolean) {
    paintTab(g, rect, tabColor)

    val thickness = theme.thickness

    val underline = when(position) {
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y, rect.width, thickness)
      JBTabsPosition.top -> Rectangle(rect.x, rect.y + rect.height - thickness, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      else -> Rectangle(rect.x, rect.y, thickness, rect.height)
    }

    g.color = if(active) theme.underline else theme.inactiveUnderline
    g.fillRect(underline.x, underline.y, underline.width, underline.height)
  }

}