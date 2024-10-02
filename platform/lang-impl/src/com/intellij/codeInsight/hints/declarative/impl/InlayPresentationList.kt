// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.LightweightHint
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.enumMapOf
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.withTranslated
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

/**
 * @see PresentationTreeBuilderImpl
 */
@ApiStatus.Internal
class InlayPresentationList(
  private var state: TinyTree<Any?>,
  @TestOnly var hintFormat: HintFormat,
  @TestOnly var isDisabled: Boolean,
  var payloads: Map<String, InlayActionPayload>? = null,
  private val providerClass: Class<*>,
  @NlsContexts.HintText private val tooltip: String?,
  internal val sourceId: String,
) {
  companion object {
    private val NOT_COMPUTED = DeclarativeHintWidth(-1, -1, -1)
    private val MARGIN_PADDING_BY_FORMAT = enumMapOf<HintMarginPadding, Pair<Int, Int>>().apply {
      put(HintMarginPadding.OnlyPadding, 0 to 7)
      put(HintMarginPadding.MarginAndSmallerPadding, 2 to 6)
    }
    private const val ARC_WIDTH = 8
    private const val ARC_HEIGHT = 8
    private const val BACKGROUND_ALPHA: Float = 0.55f
  }

  private var entries: Array<InlayPresentationEntry> = PresentationEntryBuilder(state, providerClass).buildPresentationEntries()
  private var _partialWidthSums: IntArray? = null
  private var computedWidth: DeclarativeHintWidth = NOT_COMPUTED
  private var size: Float = Float.MAX_VALUE
  private var fontName: String = ""

  private fun computePartialSums(fontMetricsStorage: InlayTextMetricsStorage): IntArray {
    var width = 0
    return IntArray(entries.size) {
      val entry = entries[it]
      val oldWidth = width
      width += entry.computeWidth(getMetrics(fontMetricsStorage))
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
    SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
      entry.handleClick(e, this, controlDown)
    }
  }

  private fun findEntryByPoint(fontMetricsStorage: InlayTextMetricsStorage, pointInsideInlay: Point): InlayPresentationEntry? {
    val x = pointInsideInlay.x
    val partialWidthSums = getPartialWidthSums(fontMetricsStorage)
    val hintWidth = getWidthInPixels(fontMetricsStorage)
    for ((index, entry) in entries.withIndex()) {
      val leftBound = partialWidthSums[index] + hintWidth.marginAndPadding
      val rightBound = partialWidthSums.getOrNull(index + 1)?.let { it + hintWidth.marginAndPadding }
                       ?: Int.MAX_VALUE

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
  fun updateState(state: TinyTree<Any?>, disabled: Boolean, hintFormat: HintFormat) {
    updateStateTree(state, this.state, 0, 0)
    this.state = state
    this.entries = PresentationEntryBuilder(state, providerClass).buildPresentationEntries()
    this.computedWidth = NOT_COMPUTED
    this._partialWidthSums = null
    this.isDisabled = disabled
    this.hintFormat = hintFormat
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
    updateState(state, isDisabled, hintFormat)
  }

  fun getWidthInPixels(textMetricsStorage: InlayTextMetricsStorage): DeclarativeHintWidth {
    val metrics = getMetrics(textMetricsStorage)
    val isActual = metrics.isActual(size, fontName)
    if (!isActual || computedWidth == NOT_COMPUTED) {
      size = metrics.font.size2D
      fontName = metrics.font.family
      val textWidth = entries.sumOf { it.computeWidth(metrics) }
      val (margin, padding) = MARGIN_PADDING_BY_FORMAT[hintFormat.horizontalMarginPadding]!!
      computedWidth = DeclarativeHintWidth(margin, padding, textWidth)
      return computedWidth
    }
    return computedWidth
  }

  data class DeclarativeHintWidth(
    internal val margin: Int,
    internal val padding: Int,
    internal val textWidth: Int,
  ) {
    val marginAndPadding: Int get() = margin + padding
    val boxWidth: Int get() = 2 * padding + textWidth
    val fullWidth: Int get() = 2 * margin + boxWidth
  }

  fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    val editor = inlay.editor as EditorImpl
    val storage = InlayHintsUtils.getTextMetricStorage(editor)
    var xOffset = 0
    val metrics = getMetrics(storage)
    val gap =  if (targetRegion.height.toInt() < metrics.lineHeight + 2) 1 else 2
    val attrKey = when (hintFormat.colorKind) {
      HintColorKind.Default -> DefaultLanguageHighlighterColors.INLAY_DEFAULT
      HintColorKind.Parameter -> DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT
      HintColorKind.TextWithoutBackground -> DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND
    }
    val attrs = editor.colorsScheme.getAttributes(attrKey)
    g.withTranslated(targetRegion.x, targetRegion.y) {
      if (hintFormat.colorKind.hasBackground()) {
        val rectHeight = targetRegion.height.toInt() - gap * 2
        val rectWidth = getWidthInPixels(storage)
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.color = attrs.backgroundColor ?: textAttributes.backgroundColor
        g.fillRoundRect(rectWidth.margin, gap, rectWidth.boxWidth, rectHeight, ARC_WIDTH, ARC_HEIGHT)
        config.restore()
      }
    }


    g.withTranslated(getWidthInPixels(storage).marginAndPadding + targetRegion.x, targetRegion.y) {
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
          entry.render(g, metrics, finalAttrs, isDisabled, gap, targetRegion.height.toInt(), editor)
        }
        xOffset += entry.computeWidth(metrics)
      }
    }
  }

  private fun getMetrics(fontMetricsStorage: InlayTextMetricsStorage): InlayTextMetrics =
    fontMetricsStorage.getFontMetrics(small = hintFormat.fontSize == HintFontSize.ABitSmallerThanInEditor)

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
      hintFormat,
      state,
      providerId,
      isDisabled,
      payloads?.map { (name, action) -> InlayPayload(name, action) },
      providerClass,
      sourceId,
    )
  }
}