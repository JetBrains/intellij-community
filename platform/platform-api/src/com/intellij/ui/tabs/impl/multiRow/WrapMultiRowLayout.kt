// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl

class WrapMultiRowLayout(tabs: JBTabsImpl, showPinnedTabsSeparately: Boolean) : MultiRowLayout(tabs, showPinnedTabsSeparately) {
  override fun splitToRows(data: MultiRowPassInfo): List<TabsRow> {
    val leftmostX = data.toFitRec.x + tabs.titleWrapper.preferredSize.width
    val entryToolbarWidth = tabs.entryPointToolbar?.component?.let { toolbar ->
      tabs.getActionsInsets().left + toolbar.preferredSize.width + tabs.getActionsInsets().right
    } ?: 0
    val rightmostX = data.toFitRec.x + data.toFitRec.width - entryToolbarWidth
    val firstRowWidth = rightmostX - leftmostX
    val getRowMaxLen: (Int) -> Int = { index -> if (index == 0) firstRowWidth else data.toFitRec.width }

    val infos = data.myVisibleInfos
    val rows = mutableListOf<TabsRow>()
    if (showPinnedTabsSeparately) {
      val (pinned, unpinned) = splitToPinnedUnpinned(infos)
      if (pinned.isNotEmpty()) {
        rows.add(CompressibleTabsRow(pinned, withTitle = tabs.titleWrapper.preferredSize.width > 0,
                                     withEntryPointToolbar = tabs.entryPointPreferredSize.width > 0))
      }
      doSplitToRows(data, rows, unpinned, getRowMaxLen)
    }
    else {
      doSplitToRows(data, rows, infos, getRowMaxLen)
    }

    return rows
  }

  private fun doSplitToRows(data: MultiRowPassInfo,
                            rows: MutableList<TabsRow>,
                            infosToSplit: List<TabInfo>,
                            getRowMaxLen: (index: Int) -> Int) {
    var curRowInfos = mutableListOf<TabInfo>()
    var curLen = 0
    for (info in infosToSplit) {
      val len = tabs.infoToLabel.get(info)!!.preferredSize.width
      data.lengths[info] = len
      if (curLen + len <= getRowMaxLen(rows.size)) {
        curRowInfos.add(info)
      }
      else {
        rows.add(createRow(curRowInfos, isFirst = rows.size == 0))
        curRowInfos = mutableListOf(info)
        curLen = 0
      }
      curLen += len + tabs.tabHGap
    }
    if (curRowInfos.isNotEmpty()) {
      rows.add(createRow(curRowInfos, isFirst = rows.size == 0))
    }
  }

  private fun createRow(infos: List<TabInfo>, isFirst: Boolean): TabsRow {
    return SimpleTabsRow(infos,
                         withTitle = isFirst && tabs.titleWrapper.preferredSize.width > 0,
                         withEntryPointToolbar = isFirst && tabs.entryPointPreferredSize.width > 0)
  }
}