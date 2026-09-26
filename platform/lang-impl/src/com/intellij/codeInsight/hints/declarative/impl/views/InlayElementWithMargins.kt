// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import org.jetbrains.annotations.ApiStatus
import java.awt.Point

@ApiStatus.Internal
interface InlayElementWithMargins<Context> {
  fun computeLeftMargin(context: Context): Int

  fun computeRightMargin(context: Context): Int

  // content and padding (and not including margin)
  fun computeBoxWidth(context: Context): Int
}

@ApiStatus.Internal
fun <Context> InlayElementWithMargins<Context>.computeFullWidth(context: Context): Int =
  computeLeftMargin(context) + computeBoxWidth(context) + computeRightMargin(context)

@ApiStatus.Internal
abstract class InlayElementWithMarginsCompositeBase<Context, SubView, SubViewContext>()
  : Invalidable
  where SubView : InlayElementWithMargins<SubViewContext>,
        SubView : Invalidable {
  protected abstract fun getSubView(index: Int): SubView
  protected abstract val subViewCount: Int
  abstract fun computeSubViewContext(context: Context): SubViewContext

  private var computedSubViewMetrics: SubViewMetrics? = null

  protected inline fun forEachSubViewBounds(
    context: Context,
    action: (SubView, Int, Int) -> Unit,
  ) {
    val sortedBounds = getSubViewMetrics(context).sortedBounds
    for (index in 0..<subViewCount) {
      val leftBound = sortedBounds[2 * index]
      val rightBound = sortedBounds[2 * index + 1]
      action(getSubView(index), leftBound, rightBound)
    }
  }

  override fun invalidate() {
    computedSubViewMetrics = null
    for (index in 0..<subViewCount) {
      getSubView(index).invalidate()
    }
  }

  protected inline fun <R> forSubViewAtPoint(
    pointInsideInlay: Point,
    context: Context,
    action: (SubView, Point) -> R,
  ) : R? {
    val x = pointInsideInlay.x
    forEachSubViewBounds(context) { subView, leftBound, rightBound ->
      if (x in leftBound..<rightBound) {
        return action(subView, Point(x - leftBound, pointInsideInlay.y))
      }
    }
    return null
  }

  open fun getSubViewMetrics(context: Context): SubViewMetrics {
    val metrics = computedSubViewMetrics
    if (metrics == null) {
      val computed = computeSubViewMetrics(context)
      computedSubViewMetrics = computed
      return computed
    }
    return metrics
  }

  private fun computeSubViewMetrics(
    context: Context,
  ): SubViewMetrics {
    val subViewContext = computeSubViewContext(context)
    val sortedBounds = IntArray(subViewCount * 2)
    var xSoFar = 0
    var previousRightMargin = 0
    for (index in 0..<subViewCount) {
      val subView = getSubView(index)
      val leftMargin = subView.computeLeftMargin(subViewContext)
      val leftBound = xSoFar + maxOf(previousRightMargin, leftMargin)
      val rightBound = leftBound + subView.computeBoxWidth(subViewContext)
      sortedBounds[2 * index] = leftBound
      sortedBounds[2 * index + 1] = rightBound
      previousRightMargin = subView.computeRightMargin(subViewContext)
      xSoFar = rightBound
    }
    return SubViewMetrics(sortedBounds, sortedBounds.last() + previousRightMargin)
  }
}

/**
 * [sortedBounds] is an array of left- and right-bound offset pairs along the x-axis.
 * - The left-margin always starts at 0 offset.
 *
 * [fullWidth] is the last right-bound + margin of the last element.
 */
@ApiStatus.Internal
class SubViewMetrics(val sortedBounds: IntArray, val fullWidth: Int) {
  val leftMargin: Int
    get() = sortedBounds.first()
  val rightMargin: Int
    get() = fullWidth - sortedBounds.last()
  val boxWidth: Int
    get() = sortedBounds.last() - sortedBounds.first()
}
