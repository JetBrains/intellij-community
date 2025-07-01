// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStamp
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import org.jetbrains.annotations.ApiStatus
import java.awt.Point

@ApiStatus.Internal
interface ViewWithMargins {
  val margin: Int
  fun getBoxWidth(storage: InlayTextMetricsStorage, forceUpdate: Boolean = false): Int
}

@ApiStatus.Internal
abstract class ViewWithMarginsCompositeBase<SubView>(private val ignoreInitialMargin: Boolean)
  where SubView : ViewWithMargins {
  protected abstract fun getSubView(index: Int): SubView
  protected abstract val subViewCount: Int

  private var computedSubViewMetrics: SubViewMetrics? = null
  private var inlayTextMetricsStamp: InlayTextMetricsStamp? = null

  protected inline fun forEachSubViewBounds(
    fontMetricsStorage: InlayTextMetricsStorage,
    action: (SubView, Int, Int) -> Unit,
  ) {
    val sortedBounds = getSubViewMetrics(fontMetricsStorage).sortedBounds
    for (index in 0..<subViewCount) {
      val leftBound = sortedBounds[2 * index]
      val rightBound = sortedBounds[2 * index + 1]
      action(getSubView(index), leftBound, rightBound)
    }
  }

  internal fun invalidateComputedSubViewMetrics() {
    computedSubViewMetrics = null
  }

  protected inline fun forSubViewAtPoint(
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
    action: (SubView, Point) -> Unit,
  ) {
    val x = pointInsideInlay.x.toInt()
    forEachSubViewBounds(fontMetricsStorage) { subView, leftBound, rightBound ->
      if (x in leftBound..<rightBound) {
        action(subView, Point(x - leftBound, pointInsideInlay.y))
        return
      }
    }
  }

  protected fun getSubViewMetrics(fontMetricsStorage: InlayTextMetricsStorage): SubViewMetrics {
    val metrics = computedSubViewMetrics
    val currentStamp = getCurrentTextMetricsStamp(fontMetricsStorage)
    val areFontMetricsActual = areFontMetricsActual(currentStamp)
    if (metrics == null || !areFontMetricsActual) {
      val computed = computeSubViewMetrics(ignoreInitialMargin, fontMetricsStorage, !areFontMetricsActual)
      computedSubViewMetrics = computed
      inlayTextMetricsStamp = currentStamp
      return computed
    }
    return metrics
  }

  private fun computeSubViewMetrics(
    ignoreInitialMargin: Boolean,
    fontMetricsStorage: InlayTextMetricsStorage,
    forceUpdate: Boolean
  ): SubViewMetrics {
    val sortedBounds = IntArray(subViewCount * 2)
    var xSoFar = 0
    var previousMargin = 0
    getSubView(0).let { subView ->
      val margin = subView.margin
      sortedBounds[0] = if (ignoreInitialMargin) 0 else subView.margin
      sortedBounds[1] = sortedBounds[0] + subView.getBoxWidth(fontMetricsStorage, forceUpdate)
      previousMargin = margin
    }
    xSoFar = sortedBounds[1]
    for (index in 1..<subViewCount) {
      val subView = getSubView(index)
      val margin = subView.margin
      val leftBound = xSoFar + maxOf(previousMargin, margin)
      val rightBound = leftBound + subView.getBoxWidth(fontMetricsStorage, forceUpdate)
      sortedBounds[2 * index] = leftBound
      sortedBounds[2 * index + 1] = rightBound
      previousMargin = margin
      xSoFar = rightBound
    }
    return SubViewMetrics(sortedBounds, sortedBounds.last() + previousMargin)
  }

  protected open fun getCurrentTextMetricsStamp(fontMetricsStorage: InlayTextMetricsStorage): InlayTextMetricsStamp? {
    return fontMetricsStorage.getCurrentStamp()
  }

  protected open fun areFontMetricsActual(currentStamp: InlayTextMetricsStamp?): Boolean {
    return inlayTextMetricsStamp == currentStamp
  }
}

@ApiStatus.Internal
class SubViewMetrics(val sortedBounds: IntArray, val fullWidth: Int)
