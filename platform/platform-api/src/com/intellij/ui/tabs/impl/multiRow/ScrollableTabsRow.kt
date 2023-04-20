// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import java.awt.Rectangle
import kotlin.math.abs
import kotlin.math.max

class ScrollableTabsRow(infos: List<TabInfo>,
                        withTitle: Boolean,
                        withEntryPointToolbar: Boolean
) : TabsRow(infos, withTitle, withEntryPointToolbar) {
  override fun layoutTabs(data: MultiRowPassInfo, x: Int, y: Int, maxLength: Int) {
    val tabs = data.tabs
    val lengths = infos.map { info ->
      val len = tabs.infoToLabel.get(info)!!.preferredSize.width
      data.lengths[info] = len
      len
    }

    data.reqLength = lengths.sum() + tabs.tabHGap * (infos.size - 1)

    val tabsLength = if (data.reqLength > maxLength) {
      val moreWidth = tabs.moreToolbarPreferredSize.width
      val moreX = x + maxLength - moreWidth + if (withEntryPointToolbar) tabs.getActionsInsets().left else -tabs.getActionsInsets().right
      data.moreRect = Rectangle(moreX, y, moreWidth, data.rowHeight)
      data.moreRect.x - tabs.getActionsInsets().left - x
    }
    else maxLength

    data.tabsLength = tabsLength

    val hGap = tabs.tabHGap
    var curX = x - data.scrollOffset
    for (index in infos.indices) {
      val info = infos[index]
      var len = lengths[index]
      if (curX + len > x + tabsLength) {
        len = max(0, x + tabsLength - curX)
      }
      val label = tabs.infoToLabel.get(info)!!
      val effectiveLen = if (len <= abs(hGap)) 0 else len
      tabs.layout(label, curX, y, effectiveLen, data.rowHeight)
      curX += len + hGap
    }
  }
}