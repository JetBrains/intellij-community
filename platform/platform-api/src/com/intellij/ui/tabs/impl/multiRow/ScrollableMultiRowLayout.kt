// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.LayoutPassInfo
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class ScrollableMultiRowLayout(tabs: JBTabsImpl,
                               showPinnedTabsSeparately: Boolean,
                               private val isWithScrollBar: Boolean = false) : MultiRowLayout(tabs, showPinnedTabsSeparately) {
  private var scrollOffset: Int = 0

  override fun layoutTable(visibleInfos: List<TabInfo>): LayoutPassInfo {
    if (!tabs.isMouseInsideTabsArea && !tabs.isHideTabs && !tabs.isScrollBarAdjusting() && !tabs.isRecentlyActive) {
      scrollToSelectedTab()
    }
    return super.layoutTable(visibleInfos)
  }

  override fun splitToRows(data: MultiRowPassInfo): List<TabsRow> {
    val (pinned, unpinned) = splitToPinnedUnpinned(data.visibleInfos)
    val withTitle = tabs.titleWrapper.preferredSize.width > 0
    val withEntryPoint = tabs.entryPointPreferredSize.width > 0
    return if (pinned.isNotEmpty() && unpinned.isNotEmpty()) {
      listOf(CompressibleTabsRow(pinned, withTitle, withEntryPoint),
             ScrollableTabsRow(unpinned, withTitle = false, withEntryPointToolbar = false))
    }
    else if (pinned.isNotEmpty()) {
      listOf(CompressibleTabsRow(pinned, withTitle, withEntryPoint))
    }
    else listOf(ScrollableTabsRow(unpinned, withTitle, withEntryPoint))
  }

  override fun getScrollOffset(): Int = scrollOffset

  override fun scroll(units: Int) {
    scrollOffset += units
  }

  override fun isScrollable(): Boolean = true

  override fun isWithScrollBar(): Boolean = isWithScrollBar

  private fun scrollToSelectedTab() {
    val data = prevLayoutPassInfo ?: return
    val scrollableRow = data.rows.find { it is ScrollableTabsRow } ?: return
    val selectedInfo = tabs.selectedInfo ?: return
    val minX = 0
    val maxX = data.scrollExtent
    var offset = -scrollOffset
    for (info in scrollableRow.infos) {
      val length = data.lengths[info]!!
      if (info == selectedInfo) {
        if (offset <= minX) {
          scroll(offset - minX)
        }
        else if (offset + length > maxX) {
          scroll(offset + length - maxX)
        }
        return
      }
      offset += length + tabs.tabHGap
    }
  }
}