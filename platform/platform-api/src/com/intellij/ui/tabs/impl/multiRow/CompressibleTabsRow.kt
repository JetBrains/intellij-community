// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.tabs.impl.multiRow

import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.UiDecorator
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLabel
import com.intellij.ui.tabs.impl.TabLabel.ActionsPosition
import com.intellij.util.ui.JBUI
import java.awt.Insets
import java.util.*
import java.util.function.Function
import kotlin.math.max
import kotlin.math.round

class CompressibleTabsRow(infos: List<TabInfo>,
                          withTitle: Boolean,
                          withEntryPointToolbar: Boolean
) : TabsRow(infos, withTitle, withEntryPointToolbar) {
  override fun layoutTabs(data: MultiRowPassInfo, x: Int, y: Int, maxLength: Int) {
    val tabs = data.tabs
    val decoration = tabs.uiDecorator!!.getDecoration()
    val prefLengths = infos.map {
      val label = tabs.infoToLabel.get(it)!!
      label.apply(decoration)
      label.preferredSize.width
    }
    val requiredLength = prefLengths.sum()
    val gapsLengths = tabs.tabHGap * (infos.size - 1)

    val result: CompressionResult = if (requiredLength > maxLength - gapsLengths) {
      calculateCompressibleLengths(data, prefLengths, requiredLength, maxLength - gapsLengths)
    }
    else CompressionResult(prefLengths, forcePaintBorders = false)

    var curX = x
    for ((index, info) in infos.withIndex()) {
      val label = tabs.infoToLabel.get(info)!!
      val len = result.lengths[index]
      label.isForcePaintBorders = result.forcePaintBorders
      tabs.layout(label, curX, y, len, data.rowHeight)
      curX += len + tabs.tabHGap
    }
  }

  private fun calculateCompressibleLengths(data: MultiRowPassInfo,
                                           prefLengths: List<Int>,
                                           requiredLen: Int,
                                           maxLen: Int): CompressionResult {
    val decreasedLengths = decreaseInsets(data, prefLengths, requiredLen, maxLen)
    return if (decreasedLengths.sum() > maxLen) {
      val resultLengths = decreaseMaxLengths(decreasedLengths, maxLen)
      val paintBorders = resultLengths.zip(decreasedLengths).all { it.first < it.second }
      return CompressionResult(resultLengths, paintBorders)
    }
    else CompressionResult(decreasedLengths, forcePaintBorders = false)
  }

  private fun decreaseInsets(data: MultiRowPassInfo, prefLengths: List<Int>, requiredLen: Int, maxLen: Int): List<Int> {
    val tabs = data.tabs
    val extraLen = requiredLen - maxLen
    val tabExtraLen = extraLen / infos.size
    var remainingExtraLen = extraLen % infos.size

    val cachedDecorations = LinkedList<CachedDecoration>()
    val decreasedLengths = mutableListOf<Int>()
    for (ind in infos.indices) {
      val info = infos[ind]
      var curExtraLen = tabExtraLen
      if (remainingExtraLen > 0) {
        curExtraLen++
        remainingExtraLen--
      }
      val label = tabs.infoToLabel.get(info)!!
      val actionsPosition = label.actionsPosition
      val cached = cachedDecorations.find { it.extraLen == curExtraLen && it.actionsPosition == actionsPosition }
      if (cached != null) {
        label.apply(cached.decoration)
        decreasedLengths.add(prefLengths[ind] - cached.decreasedLen)
      }
      else {
        val curInsets = createTabInsets(tabs, info)
        val decreasedInsets = calculateDecreasedInsets(curInsets, curExtraLen)
        val decoration = createUiDecoration(tabs, info, decreasedInsets)
        val decreasedLen = curInsets.sum() - decreasedInsets.sum()
        cachedDecorations.add(CachedDecoration(decoration, curExtraLen, decreasedLen, actionsPosition))

        label.apply(decoration)
        decreasedLengths.add(prefLengths[ind] - decreasedLen)
      }
      label.enableCompressionMode(true)
    }
    return decreasedLengths
  }

  private fun calculateDecreasedInsets(tabInsets: TabInsets, lenToDecrease: Int): TabInsets {
    val middleTabInsets = getMiddleTabInsets(tabInsets)
    val minTabInsets = getMinTabInsets(tabInsets)
    val middleDecreasableLen = tabInsets.sum() - middleTabInsets.sum()
    val decreasableLen = tabInsets.sum() - minTabInsets.sum()
    return if (lenToDecrease <= middleDecreasableLen) {
      val insets = decreaseProportionally(lenToDecrease, middleDecreasableLen,
                                          tabInsets.actionsInset to middleTabInsets.actionsInset,
                                          tabInsets.cornerToActions to middleTabInsets.cornerToActions)
      TabInsets(actionsPosition = tabInsets.actionsPosition,
                cornerToText = tabInsets.cornerToText,
                iconInset = tabInsets.iconInset,
                actionsInset = insets[0],
                cornerToActions = insets[1])
    }
    else if (lenToDecrease <= decreasableLen) {
      val insets = decreaseProportionally(lenToDecrease - middleDecreasableLen, decreasableLen - middleDecreasableLen,
                                          tabInsets.cornerToText to minTabInsets.cornerToText,
                                          middleTabInsets.cornerToActions to minTabInsets.cornerToActions,
                                          tabInsets.iconInset to minTabInsets.iconInset,
                                          middleTabInsets.actionsInset to minTabInsets.actionsInset)
      TabInsets(actionsPosition = tabInsets.actionsPosition,
                cornerToText = insets[0],
                cornerToActions = insets[1],
                iconInset = insets[2],
                actionsInset = insets[3])
    }
    else minTabInsets
  }

  /**
   * Decrease the sum of the provided [lengths] to the [maxLength].
   * Lengths are decreased starting from the maximum values, so the smallest lengths will be changed last.
   */
  private fun decreaseMaxLengths(lengths: List<Int>, maxLength: Int): List<Int> {
    val sorted = lengths.withIndex().sortedBy { it.value }
    val indexes = sorted.map { it.index }
    val sortedLengths = sorted.map { it.value }

    val sums = MutableList(lengths.size) { 0 }
    sums[0] = sortedLengths[0] * lengths.size
    for (ind in 1 until lengths.size) {
      sums[ind] = sums[ind - 1] + (sortedLengths[ind] - sortedLengths[ind - 1]) * (lengths.size - ind)
    }

    // Index in the sortedLengths list from which we need to decrease all lengths.
    // Lengths with index less than this index will not be changed.
    // No need to use binary search here, because size is small
    val index = sums.indexOfFirst { it >= maxLength }
    val decreasableLen = maxLength - sortedLengths.subList(0, index).sum()
    val avgLen = decreasableLen / (lengths.size - index)
    var remainingLen = decreasableLen % (lengths.size - index)

    val result = MutableList(lengths.size) { 0 }
    for (ind in sortedLengths.indices) {
      val initialIndex = indexes[ind]
      if (ind < index) {
        result[initialIndex] = sortedLengths[ind]
      }
      else {
        result[initialIndex] = avgLen
        if (remainingLen > 0) {
          result[initialIndex]++
          remainingLen--
        }
      }
    }
    return result
  }

  private fun decreaseProportionally(lenToDecrease: Int, limit: Int, vararg curLenToMinLen: Pair<Int, Int>): List<Int> {
    val ratio = 1 - lenToDecrease / limit.toFloat()
    val result = mutableListOf<Int>()
    for ((curLen, minLen) in curLenToMinLen) {
      result.add(max((curLen * ratio).toInt(), minLen))
    }

    var remainingLen = curLenToMinLen.sumOf { it.first } - lenToDecrease - result.sum()
    while (remainingLen > 0) {
      for (ind in result.indices) {
        if (remainingLen > 0) {
          remainingLen--
          result[ind]++
        }
      }
    }
    return result
  }

  private fun createTabInsets(tabs: JBTabsImpl, info: TabInfo): TabInsets {
    val decoration = TabLabel.mergeUiDecorations(tabs.uiDecorator!!.getDecoration(),
                                                 JBTabsImpl.defaultDecorator.getDecoration())
    val actionsPosition = tabs.infoToLabel.get(info)!!.actionsPosition
    val contentInsets = decoration.contentInsetsSupplier.apply(actionsPosition)
    val cornerToText = if (actionsPosition == ActionsPosition.RIGHT) {
      decoration.labelInsets.left + contentInsets.left
    }
    else decoration.labelInsets.right + contentInsets.right
    val cornerToActions = when (actionsPosition) {
      ActionsPosition.RIGHT -> decoration.labelInsets.right
      ActionsPosition.LEFT -> decoration.labelInsets.left
      ActionsPosition.NONE -> decoration.labelInsets.left + contentInsets.left
    }
    val actionsInset = when (actionsPosition) {
      ActionsPosition.RIGHT -> contentInsets.right
      ActionsPosition.LEFT -> contentInsets.left
      ActionsPosition.NONE -> 0
    }
    return TabInsets(
      actionsPosition = actionsPosition,
      cornerToText = cornerToText,
      cornerToActions = cornerToActions,
      iconInset = decoration.iconTextGap,
      actionsInset = actionsInset)
  }

  @Suppress("UseDPIAwareInsets")
  private fun createUiDecoration(tabs: JBTabsImpl, info: TabInfo, insets: TabInsets): UiDecorator.UiDecoration {
    val actionsPosition = tabs.infoToLabel.get(info)!!.actionsPosition
    val cornerToActions = insets.cornerToActions + if (actionsPosition == ActionsPosition.NONE && insets.actionsInset > 0) insets.actionsInset else 0
    val contentInsets = Insets(0, if (actionsPosition == ActionsPosition.LEFT) insets.actionsInset else 0,
                               0, if (actionsPosition == ActionsPosition.RIGHT) insets.actionsInset else 0)
    val originalDec = TabLabel.mergeUiDecorations(tabs.uiDecorator!!.getDecoration(), JBTabsImpl.defaultDecorator.getDecoration())
    val labelInsets = Insets(originalDec.labelInsets.top,
                             if (actionsPosition == ActionsPosition.RIGHT) insets.cornerToText else cornerToActions,
                             originalDec.labelInsets.bottom,
                             if (actionsPosition == ActionsPosition.RIGHT) cornerToActions else insets.cornerToText)
    return UiDecorator.UiDecoration(
      labelFont = null,
      labelInsets = labelInsets,
      contentInsetsSupplier = Function { contentInsets },
      iconTextGap = insets.iconInset
    )
  }

  private class CachedDecoration(val decoration: UiDecorator.UiDecoration,
                                 val extraLen: Int,
                                 val decreasedLen: Int,
                                 val actionsPosition: ActionsPosition)

  private class CompressionResult(val lengths: List<Int>, val forcePaintBorders: Boolean)

  private data class TabInsets(val actionsPosition: ActionsPosition,
                               val cornerToText: Int,
                               val iconInset: Int,
                               val actionsInset: Int,
                               val cornerToActions: Int) {
    fun sum(): Int = cornerToText + iconInset + actionsInset + cornerToActions
  }

  private fun getMinTabInsets(curInsets: TabInsets): TabInsets {
    return TabInsets(
      actionsPosition = curInsets.actionsPosition,
      cornerToText = max(roundToInt(curInsets.cornerToText * CORNER_TO_TEXT_INSET_RATIO),
                         JBUI.scale(CORNER_TO_TEXT_INSET_MIN)),
      cornerToActions = max(roundToInt(curInsets.cornerToActions * CORNER_TO_ACTIONS_INSET_RATIO),
                            JBUI.scale(CORNER_TO_ACTIONS_INSET_MIN)),
      iconInset = roundToInt(curInsets.iconInset * ICON_INSET_RATIO),
      actionsInset = roundToInt(curInsets.actionsInset * ACTIONS_INSET_RATIO)
    )
  }

  private fun getMiddleTabInsets(curInsets: TabInsets): TabInsets {
    return TabInsets(
      actionsPosition = curInsets.actionsPosition,
      cornerToText = curInsets.cornerToText,
      cornerToActions = max(roundToInt(curInsets.cornerToActions * CORNER_TO_ACTIONS_MID_INSET_RATIO),
                            JBUI.scale(CORNER_TO_ACTIONS_INSET_MIN)),
      iconInset = curInsets.iconInset,
      actionsInset = roundToInt(curInsets.actionsInset * ACTIONS_INSET_RATIO)
    )
  }

  private fun roundToInt(value: Float): Int = round(value).toInt()

  companion object {
    private const val CORNER_TO_TEXT_INSET_RATIO = 0.33f
    private const val ICON_INSET_RATIO = 0.5f
    private const val ACTIONS_INSET_RATIO = 0f
    private const val CORNER_TO_ACTIONS_MID_INSET_RATIO = 0.75f
    private const val CORNER_TO_ACTIONS_INSET_RATIO = 0.5f

    private const val CORNER_TO_TEXT_INSET_MIN = 4
    private const val CORNER_TO_ACTIONS_INSET_MIN = 4
  }
}