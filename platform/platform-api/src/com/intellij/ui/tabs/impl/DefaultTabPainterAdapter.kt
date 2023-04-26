// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabPainter
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class DefaultTabPainterAdapter(val painter: JBTabPainter): TabPainterAdapter {
  override val tabPainter: JBTabPainter
    get() = painter

  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    val info = label.info
    val isSelected = info == tabs.selectedInfo

    val rect = Rectangle(0, 0, label.width, label.height)

    val g2d = g as Graphics2D
    if (isSelected && tabs.getVisibleInfos().size > 1) {
      painter
        .paintSelectedTab(tabs.position, g2d, rect, tabs.borderThickness, info.tabColor, tabs.isActiveTabs(info),
                          tabs.isHoveredTab(label))
    }
    else {
      painter.paintTab(tabs.position, g2d, rect, tabs.borderThickness, info.tabColor, tabs.isActiveTabs(info), tabs.isHoveredTab(label) && tabs.getVisibleInfos().size > 1)
    }
  }
}