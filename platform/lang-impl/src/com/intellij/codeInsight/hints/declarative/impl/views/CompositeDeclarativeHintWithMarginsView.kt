// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeHintViewWithMargins
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.declarative.impl.InlayPresentationList
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.Collections

@ApiStatus.Internal
abstract class CompositeDeclarativeHintWithMarginsView<Model, SubView>(private val ignoreInitialMargin: Boolean)
  : DeclarativeHintView<Model>
  where SubView : DeclarativeHintViewWithMargins {
  protected abstract fun getSubView(index: Int): SubView

  protected abstract val subViewCount: Int

  private var computedSubViewMetrics: SubViewMetrics? = null

  private fun getSubViewMetrics(fontMetricsStorage: InlayTextMetricsStorage): SubViewMetrics {
    return computedSubViewMetrics
           ?: computeSubViewMetrics(ignoreInitialMargin, fontMetricsStorage)
             .also { computedSubViewMetrics = it }
  }

  override fun calcWidthInPixels(inlay: Inlay<*>, fontMetricsStorage: InlayTextMetricsStorage): Int {
    return getSubViewMetrics(fontMetricsStorage).fullWidth
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes, fontMetricsStorage: InlayTextMetricsStorage) {
    forEachSubViewBounds(fontMetricsStorage) { subView, leftBound, rightBound ->
      val width = rightBound - leftBound
      val currentRegion = Rectangle(targetRegion.x.toInt() + leftBound, targetRegion.y.toInt(), width, targetRegion.height.toInt())
      subView.paint(inlay, g, currentRegion, textAttributes, fontMetricsStorage)
    }
  }

  override fun handleLeftClick(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage, controlDown: Boolean) {
    forSubViewAtPoint(pointInsideInlay, fontMetricsStorage) { subView, translated ->
      subView.handleLeftClick(e, translated, fontMetricsStorage, controlDown)
    }
  }

  override fun handleHover(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage): LightweightHint? {
    forSubViewAtPoint(pointInsideInlay, fontMetricsStorage) { subView, translated ->
      return subView.handleHover(e, translated, fontMetricsStorage)
    }
    return null
  }

  override fun handleRightClick(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage) {
    forSubViewAtPoint(pointInsideInlay, fontMetricsStorage) { subView, translated ->
      subView.handleRightClick(e, translated, fontMetricsStorage)
    }
  }

  override fun getMouseArea(pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage): InlayMouseArea? {
    forSubViewAtPoint(pointInsideInlay, fontMetricsStorage) { subView, translated ->
      return subView.getMouseArea(translated, fontMetricsStorage)
    }
    return null
  }

  private inline fun forSubViewAtPoint(
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

  private inline fun forEachSubViewBounds(
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

  private fun computeSubViewMetrics(
    ignoreInitialMargin: Boolean,
    fontMetricsStorage: InlayTextMetricsStorage,
  ): SubViewMetrics {
    val sortedBounds = IntArray(subViewCount * 2)
    var xSoFar = 0
    var previousMargin = 0
    getSubView(0).let { subView ->
      val margin = subView.margin
      sortedBounds[0] = if (ignoreInitialMargin) 0 else subView.margin
      sortedBounds[1] = sortedBounds[0] + subView.getBoxWidth(fontMetricsStorage)
      previousMargin = margin
    }
    xSoFar = sortedBounds[1]
    for (index in 1..<subViewCount) {
      val subView = getSubView(index)
      val margin = subView.margin
      val leftBound = xSoFar + maxOf(previousMargin, margin)
      val rightBound = leftBound + subView.getBoxWidth(fontMetricsStorage)
      sortedBounds[2 * index] = leftBound
      sortedBounds[2 * index + 1] = rightBound
      previousMargin = margin
      xSoFar = rightBound
    }
    return SubViewMetrics(sortedBounds, sortedBounds.last() + previousMargin)
  }

  internal fun invalidateComputedSubViewMetrics() {
    computedSubViewMetrics = null
  }
}

private fun CompositeDeclarativeHintWithMarginsView<*, *>.createPresentationList(inlayData: InlayData): InlayPresentationList {
  return InlayPresentationList(inlayData, this::invalidateComputedSubViewMetrics)
}

@ApiStatus.Internal
class SubViewMetrics(val sortedBounds: IntArray, val fullWidth: Int)

internal class SingleDeclarativeHintView(inlayData: InlayData)
  : CompositeDeclarativeHintWithMarginsView<InlayData, InlayPresentationList>(false) {
  val presentationList = createPresentationList(inlayData)

  override val subViewCount: Int get() = 1
  override fun getSubView(index: Int): InlayPresentationList = presentationList
  override fun updateModel(newModel: InlayData) = presentationList.updateModel(newModel)
}

internal class MultipleDeclarativeHintsView(inlayData: List<InlayData>)
  : CompositeDeclarativeHintWithMarginsView<List<InlayData>, InlayPresentationList>(true) {
  var presentationLists: List<InlayPresentationList> =
    if (inlayData.size == 1) {
      Collections.singletonList(createPresentationList(inlayData[0]))
    }
    else {
      inlayData.map { createPresentationList(it) }
    }
    private set

  override val subViewCount: Int get() = presentationLists.size
  override fun getSubView(index: Int): InlayPresentationList = presentationLists[index]
  override fun updateModel(newModel: List<InlayData>) {
    /*
    We have no reliable way to tell if the new hints are the same hints as the ones being updated or not.
    It is a trade-off between correctness and efficiency.
    Being incorrect means user CollapseState information is not preserved between DeclarativeInlayHintPasses.
    (See InlayPresentationList#toggleTreeState)
    */

    if (newModel.size == presentationLists.size) {
      // Assume same hints
      presentationLists.forEachIndexed { index, presentationList -> presentationList.updateModel(newModel[index]) }
      return
    }
    // Different set of hints from the same provider -- try distinguishing hints using their priorities (assuming those are stable).
    var oldIndex = 0
    var newIndex = 0
    val newPresentationLists = ArrayList<InlayPresentationList>(newModel.size)
    while (oldIndex < presentationLists.size && newIndex < newModel.size) {
      val oldPrio = presentationLists[oldIndex].model.position.priority
      val newPrio = newModel[newIndex].position.priority
      when {
        oldPrio == newPrio -> {
          newPresentationLists.add(presentationLists[oldIndex].also { it.updateModel(newModel[newIndex]) })
          oldIndex++
          newIndex++
        }
        oldPrio < newPrio -> {
          // assume it's more likely a hint was removed rather than some priority would change
          oldIndex++
        }
        else /* oldPrio > newPrio */ -> {
          // assume it's more likely a hint was added
          newPresentationLists.add(createPresentationList(newModel[newIndex]))
          newIndex++
        }
      }
    }
    while (newIndex < newModel.size) {
      newPresentationLists.add(createPresentationList(newModel[newIndex]))
      newIndex++
    }
    presentationLists = newPresentationLists
  }
}

private val InlayPosition.priority: Int get() = when(this) {
  is AboveLineIndentedPosition -> priority
  is EndOfLinePosition -> priority
  is InlineInlayPosition -> priority
}
