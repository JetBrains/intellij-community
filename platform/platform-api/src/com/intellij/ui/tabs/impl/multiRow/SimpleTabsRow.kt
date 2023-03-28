// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo

class SimpleTabsRow(infos: List<TabInfo>,
                    withTitle: Boolean,
                    withEntryPointToolbar: Boolean
) : TabsRow(infos, withTitle, withEntryPointToolbar) {
  override fun layoutTabs(data: MultiRowPassInfo, x: Int, y: Int, maxLength: Int) {
    val tabs = data.tabs
    var curX = x
    for (info in infos) {
      val len = data.lengths[info]!!
      val label = tabs.myInfo2Label[info]!!
      tabs.layout(label, curX, y, len, data.rowHeight)
      curX += len + tabs.tabHGap
    }
  }
}