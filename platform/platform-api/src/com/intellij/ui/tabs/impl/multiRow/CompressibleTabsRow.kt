// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo

class CompressibleTabsRow(infos: List<TabInfo>,
                          withTitle: Boolean,
                          withEntryPointToolbar: Boolean
) : TabsRow(infos, withTitle, withEntryPointToolbar) {
  override fun layoutTabs(data: MultiRowPassInfo, x: Int, y: Int, maxLength: Int) {
    val tabs = data.tabs
    val preferredLengths = infos.map { tabs.myInfo2Label[it]!!.preferredSize.width }
    val requiredLength = preferredLengths.sum() + tabs.tabHGap * (infos.size - 1)

    val lengths = if (requiredLength > maxLength) {
      val ratio: Float = maxLength.toFloat() / requiredLength.toFloat()
      preferredLengths.map { (it * ratio).toInt() }
    }
    else preferredLengths

    preferredLengths.forEachIndexed { index, len ->
      data.lengths[infos[index]] = len
    }

    var curX = x
    for ((index, info) in infos.withIndex()) {
      val label = tabs.myInfo2Label[info]!!
      val len = lengths[index]
      tabs.layout(label, curX, y, len, data.rowHeight)
      curX += len + tabs.tabHGap
    }
  }
}