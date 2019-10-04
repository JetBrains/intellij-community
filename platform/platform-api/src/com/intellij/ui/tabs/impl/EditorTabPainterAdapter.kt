// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.tabs.JBTabPainter
import com.intellij.ui.tabs.JBTabsPosition
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

class EditorTabPainterAdapter : TabPainterAdapter {
  private val magicOffset = 1
  private val painter = JBEditorTabPainter()

  override val tabPainter: JBTabPainter
    get() = painter

  override fun paintBackground(label: TabLabel, g: Graphics, tabs: JBTabsImpl) {
    val info = label.info
    val isSelected = info == tabs.selectedInfo

    val rect = Rectangle(0, 0, label.width, label.height)

    val g2d = g as Graphics2D
    if (isSelected) {
      painter
        .paintSelectedTab(tabs.position, g2d, rect, tabs.borderThickness, info.tabColor, tabs.isActiveTabs(info),
                          tabs.isHoveredTab(label))
      paintBorders(g2d, label, tabs)
    }
    else {
      painter.paintTab(tabs.position, g2d, rect, tabs.borderThickness, info.tabColor, tabs.isActiveTabs(info), tabs.isHoveredTab(label))
      paintBorders(g2d, label, tabs)
    }
  }

  private fun paintBorders(g: Graphics2D, label: TabLabel, tabs: JBTabsImpl) {
    if(tabs.position == JBTabsPosition.top && (Registry.`is`("ide.new.editor.tabs.vertical.borders") || !tabs.isSingleRow) ||
       (tabs.position == JBTabsPosition.bottom && Registry.`is`("ide.new.editor.tabs.vertical.borders"))) {

      val rect = Rectangle(0, 0, label.width, label.height)

      val bounds = label.bounds
      if (bounds.x > magicOffset) {
        painter.paintLeftGap(tabs.position, g, rect, tabs.borderThickness)
      }

      if (bounds.x + bounds.width < tabs.width - magicOffset) {
        painter.paintRightGap(tabs.position, g, rect, tabs.borderThickness)
      }
    }
  }
}