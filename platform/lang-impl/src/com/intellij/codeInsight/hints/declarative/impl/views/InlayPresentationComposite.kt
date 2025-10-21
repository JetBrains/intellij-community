// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.*

internal class InlayPresentationComposite(inlayData: List<InlayData>)
  : InlayTopLevelElement<List<InlayData>>,
    InlayElementWithMarginsCompositeBase<InlayTextMetricsStorage, InlayPresentationList, InlayTextMetricsStorage>() {
  var presentationLists: List<InlayPresentationList> =
    if (inlayData.size == 1) {
      listOf((InlayPresentationList(inlayData.first())))
    }
    else {
      inlayData.map { InlayPresentationList(it) }
    }
    private set

  override val subViewCount: Int get() = presentationLists.size
  override fun getSubView(index: Int): InlayPresentationList = presentationLists[index]

  override fun computeSubViewContext(context: InlayTextMetricsStorage): InlayTextMetricsStorage = context

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes, textMetricsStorage: InlayTextMetricsStorage) {
    forEachSubViewBounds(textMetricsStorage) { subView, leftBound, rightBound ->
      val width = rightBound - leftBound
      val currentRegion = Rectangle(targetRegion.x.toInt() + leftBound, targetRegion.y.toInt(), width, targetRegion.height.toInt())
      subView.paint(inlay, g, currentRegion, textAttributes, textMetricsStorage)
    }
  }

  override fun findEntryAtPoint(pointInsideInlay: Point, textMetricsStorage: InlayTextMetricsStorage): CapturedPointInfo? {
    forSubViewAtPoint(pointInsideInlay, textMetricsStorage) { subView, pointInsideSubView ->
      return subView.findEntryAtPoint(pointInsideSubView, textMetricsStorage)
    }
    return null
  }
  override fun updateModel(newModel: List<InlayData>) {
    /* We have no reliable way to tell if the new hints are the same hints as the ones being updated or not.
       It is a trade-off between correctness and efficiency.
       Being incorrect means user CollapseState information is not preserved between DeclarativeInlayHintPasses.
       (See InlayPresentationList#toggleTreeState) */
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
          newPresentationLists.add(InlayPresentationList(newModel[newIndex]))
          newIndex++
        }
      }
    }
    while (newIndex < newModel.size) {
      newPresentationLists.add(InlayPresentationList(newModel[newIndex]))
      newIndex++
    }
    presentationLists = newPresentationLists
    invalidate()
  }

  override fun computeLeftMargin(context: InlayTextMetricsStorage): Int = 0

  override fun computeRightMargin(context: InlayTextMetricsStorage): Int = 0

  override fun computeBoxWidth(context: InlayTextMetricsStorage): Int = getSubViewMetrics(context).fullWidth
}

private val InlayPosition.priority: Int get() = when(this) {
  is AboveLineIndentedPosition -> priority
  is EndOfLinePosition -> priority
  is InlineInlayPosition -> priority
}
