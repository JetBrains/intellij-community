// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabPainter
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class DefaultTabPainterAdapter(override val tabPainter: JBTabPainter) : TabPainterAdapter {
  @Internal
  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    val info = label.info
    val rect = Rectangle(0, 0, label.width, label.height)
    val g2d = g as Graphics2D
    if (info == tabs.selectedInfo && tabs.getVisibleInfos().size > 1) {
      tabPainter.paintSelectedTab(
        position = tabs.tabsPosition,
        g = g2d,
        rect = rect,
        borderThickness = tabs.borderThickness,
        tabColor = info.tabColor,
        active = tabs.isActiveTabs(info),
        hovered = tabs.isHoveredTab(label),
      )
    }
    else {
      tabPainter.paintTab(
        position = tabs.tabsPosition,
        g = g2d,
        rect = rect,
        borderThickness = tabs.borderThickness,
        tabColor = info.tabColor,
        active = tabs.isActiveTabs(info),
        hovered = tabs.isHoveredTab(label) && tabs.getVisibleInfos().size > 1,
      )
    }
  }
}