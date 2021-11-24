// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.tabs.JBTabPainter
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
    val isHovered = tabs.isHoveredTab(label)

    val rect = Rectangle(0, 0, label.width, label.height)

    val g2d = g as Graphics2D
    if (isSelected) {
      painter.paintSelectedTab(tabs.position, g2d, rect,
                               tabs.borderThickness, info.tabColor,
                               tabs.isActiveTabs(info), isHovered)
      paintBorders(g2d, label, tabs)
    }
    else {
      if (ExperimentalUI.isNewEditorTabs() && isHovered) {
        rect.height -= 1
      }
      painter.paintTab(tabs.position, g2d, rect, tabs.borderThickness, info.tabColor, tabs.isActiveTabs(info), isHovered)
      paintBorders(g2d, label, tabs)
    }
  }

  private fun paintBorders(g: Graphics2D, label: TabLabel, tabs: JBTabsImpl) {
    val paintStandardBorder = !tabs.isSingleRow
                              || (!tabs.position.isSide && Registry.`is`("ide.new.editor.tabs.vertical.borders"))
    val lastPinned = label.isLastPinned
    val nextToLastPinned = label.isNextToLastPinned
    val rect = Rectangle(0, 0, label.width, label.height)
    if (paintStandardBorder || lastPinned || nextToLastPinned) {


      val bounds = label.bounds
      if (bounds.x > magicOffset && (paintStandardBorder || nextToLastPinned)) {
        painter.paintLeftGap(tabs.position, g, rect, tabs.borderThickness)
      }

      if (bounds.x + bounds.width < tabs.width - magicOffset && (paintStandardBorder || lastPinned)) {
        painter.paintRightGap(tabs.position, g, rect, tabs.borderThickness)
      }
    }

    if (tabs.position.isSide && lastPinned) {
      val bounds = label.bounds
      if (bounds.y + bounds.height < tabs.height - magicOffset) {
        painter.paintBottomGap(tabs.position, g, rect, tabs.borderThickness)
      }
    }
    if (tabs.position.isSide && nextToLastPinned) {
      val bounds = label.bounds
      if (bounds.y + bounds.height < tabs.height - magicOffset) {
        painter.paintTopGap(tabs.position, g, rect, tabs.borderThickness)
      }
    }
  }
}