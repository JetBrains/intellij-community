// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.impl.JBTabsImpl

class CompressibleMultiRowLayout(tabs: JBTabsImpl, showPinnedTabsSeparately: Boolean) : MultiRowLayout(tabs, showPinnedTabsSeparately) {
  override fun splitToRows(data: MultiRowPassInfo): List<TabsRow> {
    val (pinned, unpinned) = splitToPinnedUnpinned(data.myVisibleInfos)
    val withTitle = tabs.titleWrapper.preferredSize.width > 0
    val withEntryPoint = tabs.entryPointPreferredSize.width > 0
    return if (!showPinnedTabsSeparately) {
      listOf(CompressibleTabsRow(data.myVisibleInfos, withTitle, withEntryPoint))
    }
    else if (pinned.isNotEmpty() && unpinned.isNotEmpty()) {
      listOf(CompressibleTabsRow(pinned, withTitle, withEntryPoint),
             CompressibleTabsRow(unpinned, withTitle = false, withEntryPointToolbar = false))
    }
    else if (pinned.isNotEmpty()) {
      listOf(CompressibleTabsRow(pinned, withTitle, withEntryPoint))
    }
    else listOf(CompressibleTabsRow(unpinned, withTitle, withEntryPoint))
  }
}