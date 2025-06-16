// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("removal", "DEPRECATION", "ReplaceGetOrSet")

package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.JBTabsPosition
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsUtil
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.LayoutPassInfo
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.table.TableLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.abs

@ApiStatus.Internal
sealed class MultiRowLayout(
  protected val tabs: JBTabsImpl,
  protected val showPinnedTabsSeparately: Boolean,
) : TableLayout(tabs) {
  internal var prevLayoutPassInfo: MultiRowPassInfo? = null

  override fun layoutTable(visibleInfos: List<TabInfo>): LayoutPassInfo {
    tabs.resetLayout(true)

    val insets = tabs.layoutInsets
    val toFitRec = Rectangle(insets.left, insets.top,
                             tabs.width - insets.left - insets.right,
                             tabs.height - insets.top - insets.bottom)
    val data = MultiRowPassInfo(tabs, visibleInfos, toFitRec, scrollOffset)
    prevLayoutPassInfo = data

    if (!tabs.isHideTabs && !visibleInfos.isEmpty() && !data.toFitRec.isEmpty) {
      val rows = splitToRows(data)
      data.rows.addAll(rows)
      layoutRows(data)

      val topRowInd = if (tabs.tabsPosition == JBTabsPosition.top) 0 else rows.size - 1
      data.tabsRectangle = Rectangle(toFitRec.x, getRowY(data, topRowInd), toFitRec.width, data.rowCount * data.rowHeight)
    }

    tabs.titleWrapper.bounds = data.titleRect
    tabs.moreToolbar!!.component.bounds = data.moreRect
    tabs.entryPointToolbar?.component?.bounds = data.entryPointRect

    tabs.selectedInfo?.let { layoutTabComponent(data, it) }
    return data
  }

  internal abstract fun splitToRows(data: MultiRowPassInfo): List<TabsRow>

  private fun layoutRows(data: MultiRowPassInfo) {
    for ((index, row) in data.rows.withIndex()) {
      val y = getRowY(data, index)
      row.layoutRow(data, y)
    }
  }

  private fun getRowY(data: MultiRowPassInfo, row: Int): Int {
    return when (tabs.tabsPosition) {
      JBTabsPosition.top -> {
        data.toFitRec.y + row * data.rowHeight
      }
      JBTabsPosition.bottom -> {
        data.toFitRec.y + data.toFitRec.height - (row + 1) * data.rowHeight
      }
      else -> error("MultiRowLayout is not supported for vertical placements")
    }
  }

  private fun layoutTabComponent(data: MultiRowPassInfo, info: TabInfo) {
    val toolbar = tabs.infoToToolbar.get(info)

    val componentY = when (tabs.tabsPosition) {
      JBTabsPosition.top -> data.rowCount * data.rowHeight
      JBTabsPosition.bottom -> 0
      else -> error("MultiRowLayout is not supported for vertical placements")
    }
    val componentHeight = when (tabs.tabsPosition) {
      JBTabsPosition.top -> tabs.height  // it will be adjusted inside JBTabsImpl.layoutComp
      JBTabsPosition.bottom -> tabs.height - data.rowCount * data.rowHeight
      else -> error("MultiRowLayout is not supported for vertical placements")
    }

    if (!tabs.horizontalSide && toolbar != null && !toolbar.isEmpty) {
      val toolbarWidth = toolbar.preferredSize.width
      val vSeparatorWidth = if (toolbarWidth > 0) tabs.separatorWidth else 0
      if (tabs.isSideComponentBefore) {
        val compRect = tabs.layoutComp(Rectangle(toolbarWidth + vSeparatorWidth, componentY, tabs.width, componentHeight),
                                       info.component, 0, 0)
        tabs.layout(toolbar, compRect.x - toolbarWidth - vSeparatorWidth, compRect.y, toolbarWidth, compRect.height)
      }
      else {
        val width = tabs.width - toolbarWidth - vSeparatorWidth
        val compRect = tabs.layoutComp(Rectangle(0, componentY, width, componentHeight),
                                       info.component, 0, 0)
        tabs.layout(toolbar, compRect.x + compRect.width + vSeparatorWidth, compRect.y, toolbarWidth, compRect.height)
      }
    }
    else tabs.layoutComp(Rectangle(0, componentY, tabs.width, componentHeight),
                         info.component, 0, 0)
  }

  protected fun splitToPinnedUnpinned(infosToSplit: List<TabInfo>): Pair<List<TabInfo>, List<TabInfo>> {
    val infos = infosToSplit.toList()
    val lastPinnedInd = infos.indexOfLast { it.isPinned }
    if (lastPinnedInd == -1) {
      return emptyList<TabInfo>() to infos
    }
    val pinnedRowEndInd = if (infos.getOrNull(lastPinnedInd + 1)?.let { tabs.isDropTarget(it) } == true) {
      lastPinnedInd + 1  // if next is dnd placeholder, put it to the pinned row
    }
    else lastPinnedInd
    val pinned = infos.subList(0, pinnedRowEndInd + 1)
    val unpinned = infos.subList(pinnedRowEndInd + 1, infos.size)
    return pinned to unpinned
  }

  override fun getScrollOffset(): Int {
    return 0
  }

  override fun scroll(units: Int) {
  }

  override fun isWithScrollBar(): Boolean {
    return false
  }

  override fun isDragOut(tabLabel: TabLabel, deltaX: Int, deltaY: Int): Boolean {
    val data = prevLayoutPassInfo
    if (data == null) {
      return super.isDragOut(tabLabel, deltaX, deltaY)
    }
    return abs(deltaY) > data.tabsRectangle.height * getDragOutMultiplier()
  }

  override fun getDropIndexFor(point: Point): Int {
    val tabsAt = doGetDropIndexFor(point)
    if (tabsAt != -1) return tabsAt
    val tolerance = JBUI.scale(TabsUtil.UNSCALED_DROP_TOLERANCE)
    val tabsBelow = doGetDropIndexFor(Point(point).apply { y += tolerance })
    if (tabsBelow != -1) return tabsBelow
    return doGetDropIndexFor(Point(point).apply { y -= tolerance })
  }

  private fun doGetDropIndexFor(point: Point): Int {
    val data = prevLayoutPassInfo
    if (data == null) return -1
    var result = -1

    val lastInRow = data.rows.mapNotNull { it.infos.lastOrNull() }

    var component = tabs.getComponentAt(point)
    if (component is JBTabsImpl) {
      for (i in 0 until data.visibleInfos.size - 1) {
        val firstInfo = data.visibleInfos[i]
        val secondInfo = data.visibleInfos[i + 1]
        val first = tabs.getTabLabel(firstInfo)!!
        val second = tabs.getTabLabel(secondInfo)!!
        val firstBounds = first.bounds
        val secondBounds = second.bounds
        val between = firstBounds.maxX < point.x
                      && secondBounds.getX() > point.x
                      && firstBounds.y < point.y
                      && secondBounds.maxY > point.y
        if (between) {
          component = first
          break
        }
        if (lastInRow.contains(firstInfo)
            && firstBounds.y <= point.y
            && firstBounds.maxY >= point.y
            && firstBounds.maxX <= point.x) {
          component = second
          break
        }
      }
    }

    if (component is TabLabel) {
      val info = component.info
      val index = data.visibleInfos.indexOf(info)
      if (!tabs.isDropTarget(info)) {
        val dropTargetBefore = data.visibleInfos.subList(0, index + 1).any { tabs.isDropTarget(it) }
        result = index - if (dropTargetBefore) 1 else 0
      }
      else if (index < data.visibleInfos.size) {
        result = index
      }
    }
    return result
  }
}