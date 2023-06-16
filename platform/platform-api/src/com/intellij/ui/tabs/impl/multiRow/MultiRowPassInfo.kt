// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.LayoutPassInfo
import java.awt.Rectangle

class MultiRowPassInfo(val tabs: JBTabsImpl,
                       visibleInfos: List<TabInfo>,
                       val toFitRec: Rectangle,
                       val scrollOffset: Int
) : LayoutPassInfo(visibleInfos) {
  val rows: MutableList<TabsRow> = mutableListOf()
  val lengths: MutableMap<TabInfo, Int> = HashMap()

  val rowHeight: Int
    get() = tabs.headerFitSize!!.height

  var tabsRectangle: Rectangle = Rectangle()
  var reqLength: Int = toFitRec.width - toFitRec.x
  var tabsLength: Int = reqLength

  override fun getRowCount(): Int = rows.size

  override fun getHeaderRectangle(): Rectangle = tabsRectangle.clone() as Rectangle

  override fun getRequiredLength(): Int = reqLength

  override fun getScrollExtent(): Int = tabsLength
}