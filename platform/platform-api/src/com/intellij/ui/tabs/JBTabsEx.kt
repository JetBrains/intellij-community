// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.openapi.actionSystem.DataKey
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.SwingConstants

interface JBTabsEx : JBTabs {
  companion object {
    @JvmField
    val NAVIGATION_ACTIONS_KEY: DataKey<JBTabsEx> = DataKey.create("JBTabs")
  }

  val isEditorTabs: Boolean

  fun updateTabActions(validateNow: Boolean)

  fun addTabSilently(info: TabInfo, index: Int): TabInfo?

  fun removeTab(info: TabInfo, forcedSelectionTransfer: TabInfo?)

  fun getToSelectOnRemoveOf(info: TabInfo): TabInfo?

  fun sortTabs(comparator: Comparator<TabInfo>)

  val dropInfoIndex: Int

  @get:MagicConstant(intValues = [
    SwingConstants.TOP.toLong(),
    SwingConstants.LEFT.toLong(),
    SwingConstants.BOTTOM.toLong(),
    SwingConstants.RIGHT.toLong(),
    -1,
  ])
  val dropSide: Int

  val isEmptyVisible: Boolean

  fun setTitleProducer(titleProducer: (() -> Pair<Icon, @Nls String>)?)

  /**
   * true if tabs and top toolbar should be hidden from a view
   */
  var isHideTopPanel: Boolean
}
