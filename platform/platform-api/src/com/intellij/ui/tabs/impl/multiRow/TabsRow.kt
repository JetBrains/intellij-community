// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import java.awt.Rectangle

abstract class TabsRow(val infos: List<TabInfo>, val withTitle: Boolean, val withEntryPointToolbar: Boolean) {
  fun layoutRow(data: MultiRowPassInfo, y: Int) {
    val tabsRange = layoutTitleAndEntryPoint(data, y)
    layoutTabs(data, tabsRange.first, y, tabsRange.last - tabsRange.first)
  }

  protected abstract fun layoutTabs(data: MultiRowPassInfo, x: Int, y: Int, maxLength: Int)

  private fun layoutTitleAndEntryPoint(data: MultiRowPassInfo, y: Int): IntRange {
    val tabs = data.tabs
    if (withTitle) {
      data.titleRect = Rectangle(data.toFitRec.x, y, tabs.titleWrapper.preferredSize.width, data.rowHeight)
    }
    if (withEntryPointToolbar) {
      val entryPointWidth = tabs.entryPointPreferredSize.width
      data.entryPointRect = Rectangle(data.toFitRec.x + data.toFitRec.width - entryPointWidth - tabs.getActionsInsets().right,
                                      y, entryPointWidth, data.rowHeight)
    }
    val leftmostX = data.toFitRec.x + data.titleRect.width
    val rightmostX = if (withEntryPointToolbar) data.entryPointRect.x - tabs.getActionsInsets().left else data.toFitRec.x + data.toFitRec.width
    return leftmostX..rightmostX
  }

  override fun toString(): String {
    return "${javaClass.simpleName}: $infos"
  }
}