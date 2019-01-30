// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.swing.fillRect
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle

interface JBTabPainter {
  companion object {
    val EDITOR_TAB = TabTheme(thickness = JBUI.scale(3))
    val TOOLWINDOW_TAB = TabTheme(defaultTabColor = null)
  }

  @Deprecated("You should move the painting logic to an implementation of this interface")
  fun getBackgroundColor(): Color

  fun fillBackground(g : Graphics2D, rect : Rectangle)
  fun fillBeforeAfterTabs(g : Graphics2D, before : Rectangle, after : Rectangle)
  fun paintTab(g : Graphics2D, rect: Rectangle, tabColor: Color?, hovered: Boolean)
  fun paintSelectedTab(g : Graphics2D, rect: Rectangle, tabColor: Color?, position : JBTabsPosition, active: Boolean, hovered: Boolean)
}

class TabTheme(
  val background: Color = JBUI.CurrentTheme.EditorTabs.backgroundColor(),
  val defaultTabColor: Color? = JBUI.CurrentTheme.EditorTabs.defaultTabColor(),
  val underline: Color = JBUI.CurrentTheme.EditorTabs.underlineColor(),
  val inactiveUnderline: Color = JBUI.CurrentTheme.EditorTabs.inactiveUnderlineColor(),
  val hoverOverlayColor: Color = JBUI.CurrentTheme.EditorTabs.hoverOverlayColor(),
  val unselectedOverlayColor: Color = JBUI.CurrentTheme.EditorTabs.unselectedOverlayColor(),
  val thickness : Int = JBUI.scale(2)
)

class JBDefaultTabPainter(val theme : TabTheme = TabTheme()) : JBTabPainter {

  override fun getBackgroundColor(): Color = theme.background


  override fun fillBackground(g: Graphics2D, rect: Rectangle) {
    g.color = theme.background
    g.fillRect(rect)
  }

  override fun fillBeforeAfterTabs(g: Graphics2D, before: Rectangle, after: Rectangle) {
    g.color = theme.defaultTabColor ?: return
/*    val rect = Rectangle(before.x, before.y, after.x+after.width, after.y+after.height)
    g.fillRect(rect.x, rect.y, rect.width, rect.height)*/

    g.fillRect(before)
    g.fillRect(after)
  }

  override fun paintTab(g: Graphics2D, rect: Rectangle, tabColor: Color?, hovered: Boolean) {
    val background = tabColor ?: theme.defaultTabColor
    if(background != null) {
      g.color = background
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
    val background = tabColor ?: theme.defaultTabColor
    if(background != null) {
      g.color = background
      g.fillRect(rect)
    }

    if(hovered) {
      g.color = theme.hoverOverlayColor
      g.fillRect(rect)
    }

    val thickness = theme.thickness

    val underline = when(position) {
      /**
       * TODO
       */
      JBTabsPosition.bottom -> Rectangle(rect.x, rect.y - 1, rect.width, thickness)
      JBTabsPosition.top -> Rectangle(rect.x, rect.y + rect.height - thickness + 1, rect.width, thickness)
      JBTabsPosition.left -> Rectangle(rect.x + rect.width - thickness, rect.y, thickness, rect.height)
      else -> Rectangle(rect.x, rect.y, thickness, rect.height)
    }

    g.color = if(active) theme.underline else theme.inactiveUnderline
    g.fillRect(underline)
  }
}