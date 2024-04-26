// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.withTranslated
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.GraphicsUtil
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

/**
 * @see PresentationTreeBuilderImpl
 */
class InlayPresentationList(
  private var state: TinyTree<Any?>,
  @TestOnly var hasBackground: Boolean,
  @TestOnly var isDisabled: Boolean,
  var payloads: Map<String, InlayActionPayload>? = null,
  private val providerClass: Class<*>,
  @NlsContexts.HintText private val tooltip: String?
) {
  companion object {
    private const val NOT_COMPUTED = -1
    private const val LEFT_MARGIN = 7
    private const val RIGHT_MARGIN = 7
    private const val BOTTOM_MARGIN = 1
    private const val TOP_MARGIN = 1
    private const val ARC_WIDTH = 8
    private const val ARC_HEIGHT = 8
    private const val BACKGROUND_ALPHA: Float = 0.55f
  }

  private var entries: Array<InlayPresentationEntry> = PresentationEntryBuilder(state, providerClass).buildPresentationEntries()
  private var _partialWidthSums: IntArray? = null
  private var computedWidth: Int = NOT_COMPUTED
  private var size: Float = Float.MAX_VALUE
  private var fontName: String = ""

  private fun computePartialSums(fontMetricsStorage: InlayTextMetricsStorage): IntArray {
    var width = 0
    return IntArray(entries.size) {
      val entry = entries[it]
      val oldWidth = width
      width += entry.computeWidth(fontMetricsStorage)
      oldWidth
    }
  }

  private fun getPartialWidthSums(storage: InlayTextMetricsStorage): IntArray {
    val sums = _partialWidthSums
    if (sums != null) {
      return sums
    }
    val computed = computePartialSums(storage)
    _partialWidthSums = computed
    return computed
  }

  fun handleClick(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage, controlDown: Boolean) {
    val entry = findEntryByPoint(fontMetricsStorage, pointInsideInlay) ?: return
    entry.handleClick(e.editor, this, controlDown)
  }

  private fun findEntryByPoint(fontMetricsStorage: InlayTextMetricsStorage, pointInsideInlay: Point): InlayPresentationEntry? {
    val x = pointInsideInlay.x
    val partialWidthSums = getPartialWidthSums(fontMetricsStorage)
    for ((index, entry) in entries.withIndex()) {
      val leftBound = partialWidthSums[index] + LEFT_MARGIN
      val rightBound = partialWidthSums.getOrElse(index + 1) { Int.MAX_VALUE - LEFT_MARGIN } + LEFT_MARGIN

      if (x in leftBound..rightBound) {
        return entry
      }
    }
    return null
  }

  fun handleHover(e: EditorMouseEvent): LightweightHint? {
    return if (tooltip == null) null
    else PresentationFactory(e.editor).showTooltip(e.mouseEvent, tooltip)
  }

  @RequiresEdt
  fun updateState(state: TinyTree<Any?>, disabled: Boolean, hasBackground: Boolean) {
    updateStateTree(state, this.state, 0, 0)
    this.state = state
    this.entries = PresentationEntryBuilder(state, providerClass).buildPresentationEntries()
    this.computedWidth = NOT_COMPUTED
    this._partialWidthSums = null
    this.isDisabled = disabled
    this.hasBackground = hasBackground
  }

  private fun updateStateTree(treeToUpdate: TinyTree<Any?>,
                              treeToUpdateFrom: TinyTree<Any?>,
                              treeToUpdateIndex: Byte,
                              treeToUpdateFromIndex: Byte) {
    // we want to preserve the structure
    val treeToUpdateTag = treeToUpdate.getBytePayload(treeToUpdateIndex)
    val treeToUpdateFromTag = treeToUpdateFrom.getBytePayload(treeToUpdateFromIndex)
    if (treeToUpdateFromTag == InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG ||
        treeToUpdateFromTag == InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG) {
      if (treeToUpdateTag == InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG ||
          treeToUpdateTag == InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG ||
          treeToUpdateTag == InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG ||
          treeToUpdateTag == InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG) {
        treeToUpdate.setBytePayload(nodePayload = treeToUpdateFromTag, index = treeToUpdateIndex)
      }
    }

    treeToUpdateFrom.syncProcessChildren(treeToUpdateFromIndex, treeToUpdateIndex,
                                         treeToUpdate) { treeToUpdateFromChildIndex, treeToUpdateChildIndex ->
      updateStateTree(treeToUpdate, treeToUpdateFrom, treeToUpdateChildIndex, treeToUpdateFromChildIndex)
      true
    }
  }

  fun toggleTreeState(parentIndexToSwitch: Byte) {
    when (val payload = state.getBytePayload(parentIndexToSwitch)) {
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG, InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG -> {
        state.setBytePayload(InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG, parentIndexToSwitch)
      }
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG, InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG -> {
        state.setBytePayload(InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG, parentIndexToSwitch)
      }
      else -> {
        error("Unexpected payload: $payload")
      }
    }
    updateState(state, isDisabled, hasBackground)
  }

  fun getWidthInPixels(textMetricsStorage: InlayTextMetricsStorage): Int {
    val metrics = textMetricsStorage.getFontMetrics(true)
    val isActual = metrics.isActual(size, fontName)
    if (!isActual || computedWidth == NOT_COMPUTED) {
      size = metrics.font.size2D
      fontName = metrics.font.family
      val width = entries.sumOf { it.computeWidth(textMetricsStorage) } + LEFT_MARGIN + RIGHT_MARGIN
      computedWidth = width
      return width
    }
    return computedWidth
  }

  fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    val editor = inlay.editor as EditorImpl
    val storage = InlayHintsUtils.getTextMetricStorage(editor)
    var xOffset = 0
    val metrics = storage.getFontMetrics(small = false)
    val gap =  if (targetRegion.height.toInt() < metrics.lineHeight + 2) 1 else 2
    val attrKey = if (hasBackground) DefaultLanguageHighlighterColors.INLAY_DEFAULT else DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND
    val attrs = editor.colorsScheme.getAttributes(attrKey)
    g.withTranslated(targetRegion.x, targetRegion.y) {
      if (hasBackground) {
        val rectHeight = targetRegion.height.toInt() - gap * 2
        val rectWidth = getWidthInPixels(storage)
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.color = attrs.backgroundColor ?: textAttributes.backgroundColor
        g.fillRoundRect(0, gap, rectWidth, rectHeight, ARC_WIDTH, ARC_HEIGHT)
        config.restore()
      }
    }


    g.withTranslated(LEFT_MARGIN + targetRegion.x, targetRegion.y) {
      for (entry in entries) {
        val hoveredWithCtrl = entry.isHoveredWithCtrl
        val finalAttrs = if (hoveredWithCtrl) {
          val refAttrs = inlay.editor.colorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
          val inlayAttrsWithRefForeground = attrs.clone()
          inlayAttrsWithRefForeground.foregroundColor = refAttrs.foregroundColor
          inlayAttrsWithRefForeground
        }
        else {
          attrs
        }
        g.withTranslated(xOffset, 0) {
          entry.render(g, storage, finalAttrs, isDisabled, gap, targetRegion.height.toInt(), editor)
        }
        xOffset += entry.computeWidth(storage)
      }
    }
  }

  @TestOnly
  fun getEntries(): Array<InlayPresentationEntry> {
    return entries
  }

  fun getMouseArea(pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage): InlayMouseArea? {
    val entry = findEntryByPoint(fontMetricsStorage, pointInsideInlay) ?: return null
    return entry.clickArea
  }

  internal fun toInlayData(position: InlayPosition, providerId: String): InlayData {
    return InlayData(
      position,
      tooltip,
      hasBackground,
      state,
      providerId,
      isDisabled,
      payloads?.map { (name, action) -> InlayPayload(name, action) },
      providerClass,
    )
  }
}