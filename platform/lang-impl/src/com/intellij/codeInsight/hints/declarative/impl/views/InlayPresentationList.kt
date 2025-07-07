// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayActionService
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.declarative.impl.InlayTags
import com.intellij.codeInsight.hints.declarative.impl.views.PresentationEntryBuilder
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
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

@ApiStatus.Internal
interface DeclarativeHintViewWithMargins: DeclarativeHintView<InlayData> {
  val margin: Int
  fun getBoxWidth(storage: InlayTextMetricsStorage, forceUpdate: Boolean = false): Int
}

/**
 * @see com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl
 */
@ApiStatus.Internal
class InlayPresentationList(
  @ApiStatus.Internal var model: InlayData,
  private val onStateUpdated: () -> Unit
) : DeclarativeHintViewWithMargins {
  private var entries: Array<InlayPresentationEntry> = model.tree.buildPresentationEntries()
  private var _partialWidthSums: IntArray? = null

  private fun TinyTree<Any?>.buildPresentationEntries(): Array<InlayPresentationEntry> {
    return PresentationEntryBuilder(this, model.providerClass).buildPresentationEntries()
  }

  private fun computePartialSums(textMetrics: InlayTextMetrics): IntArray {
    var widthSoFar = 0
    return IntArray(entries.size) {
      val entry = entries[it]
      widthSoFar += entry.computeWidth(textMetrics)
      widthSoFar
    }
  }

  private fun getPartialWidthSums(storage: InlayTextMetricsStorage, forceRecompute: Boolean = false): IntArray {
    val sums = _partialWidthSums
    if (sums == null || forceRecompute) {
      val metrics = getMetrics(storage)
      val computed = computePartialSums(metrics)
      _partialWidthSums = computed
      return computed
    }
    return sums
  }

  override fun handleLeftClick(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
    controlDown: Boolean,
  ) {
    val entry = findEntryByPoint(fontMetricsStorage, pointInsideInlay) ?: return
    SlowOperations.startSection(SlowOperations.ACTION_PERFORM).use {
      entry.handleClick(e, this, controlDown)
    }
  }

  override fun handleRightClick(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  ) {
    service<DeclarativeInlayActionService>().invokeInlayMenu(model, e, RelativePoint(e.mouseEvent.locationOnScreen))
  }

  private val marginAndPadding: Pair<Int, Int> get() = MARGIN_PADDING_BY_FORMAT[model.hintFormat.horizontalMarginPadding]!!
  @get:ApiStatus.Internal
  override val margin: Int get() = marginAndPadding.first
  private val padding: Int get() = marginAndPadding.second
  private fun getTextWidth(storage: InlayTextMetricsStorage, forceUpdate: Boolean): Int {
    return getPartialWidthSums(storage, forceUpdate).lastOrNull() ?: 0
  }
  @ApiStatus.Internal
  override fun getBoxWidth(storage: InlayTextMetricsStorage, forceUpdate: Boolean): Int {
    return 2 * padding + getTextWidth(storage, forceUpdate)
  }

  private fun findEntryByPoint(fontMetricsStorage: InlayTextMetricsStorage, pointInsideInlay: Point): InlayPresentationEntry? {
    val partialWidthSums = getPartialWidthSums(fontMetricsStorage)
    val initialLeftBound = padding
    val x = pointInsideInlay.x - initialLeftBound
    var previousWidthSum = 0
    for ((index, entry) in entries.withIndex()) {
      val leftBound = previousWidthSum
      val rightBound = partialWidthSums[index]

      if (x in leftBound..<rightBound) {
        return entry
      }
      previousWidthSum = partialWidthSums[index]
    }
    return null
  }

  override fun handleHover(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  ): LightweightHint? {
    val tooltip = model.tooltip
    return if (tooltip == null) null
    else PresentationFactory(e.editor).showTooltip(e.mouseEvent, tooltip)
  }

  @RequiresEdt
  override fun updateModel(newModel: InlayData) {
    updateStateTree(newModel.tree, this.model.tree, 0, 0)
    this.model = newModel
    this.entries = newModel.tree.buildPresentationEntries()
    this._partialWidthSums = null
    onStateUpdated()
  }

  private fun updateStateTree(
    treeToUpdate: TinyTree<Any?>,
    treeToUpdateFrom: TinyTree<Any?>,
    treeToUpdateIndex: Byte,
    treeToUpdateFromIndex: Byte,
  ) {
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

  internal fun toggleTreeState(parentIndexToSwitch: Byte) {
    val tree = model.tree
    when (val payload = tree.getBytePayload(parentIndexToSwitch)) {
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG, InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_COLLAPSED_TAG -> {
        tree.setBytePayload(InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG, parentIndexToSwitch)
      }
      InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_EXPANDED_TAG, InlayTags.COLLAPSIBLE_LIST_IMPLICITLY_EXPANDED_TAG -> {
        tree.setBytePayload(InlayTags.COLLAPSIBLE_LIST_EXPLICITLY_COLLAPSED_TAG, parentIndexToSwitch)
      }
      else -> {
        error("Unexpected payload: $payload")
      }
    }
    updateModel(this.model)
  }

  @ApiStatus.Internal
  override fun calcWidthInPixels(inlay: Inlay<*>, textMetricsStorage: InlayTextMetricsStorage): Int = getBoxWidth(textMetricsStorage)

  override fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
    storage: InlayTextMetricsStorage,
  ) {
    val editor = inlay.editor as EditorImpl
    var xOffset = 0
    val metrics = getMetrics(storage)
    val gap = if (targetRegion.height.toInt() < metrics.lineHeight + 2) 1 else 2
    val hintFormat = model.hintFormat
    val attrKey = when (hintFormat.colorKind) {
      HintColorKind.Default -> DefaultLanguageHighlighterColors.INLAY_DEFAULT
      HintColorKind.Parameter -> DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT
      HintColorKind.TextWithoutBackground -> DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND
    }
    val attrs = editor.colorsScheme.getAttributes(attrKey)
    g.withTranslated(targetRegion.x, targetRegion.y) {
      if (hintFormat.colorKind.hasBackground()) {
        val rectHeight = targetRegion.height.toInt() - gap * 2
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.color = attrs.backgroundColor ?: textAttributes.backgroundColor
        g.fillRoundRect(0, gap, getBoxWidth(storage), rectHeight, ARC_WIDTH, ARC_HEIGHT)
        config.restore()
      }
    }


    g.withTranslated(padding + targetRegion.x, targetRegion.y) {
      val partialWidthSums = getPartialWidthSums(storage)
      for ((index, entry) in entries.withIndex()) {
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
          entry.render(g, metrics, finalAttrs, model.disabled, gap, targetRegion.height.toInt(), editor)
        }
        xOffset = partialWidthSums[index]
      }
    }
  }

  private fun getMetrics(fontMetricsStorage: InlayTextMetricsStorage): InlayTextMetrics =
    fontMetricsStorage.getFontMetrics(small = model.hintFormat.fontSize == HintFontSize.ABitSmallerThanInEditor)

  @TestOnly
  fun getEntries(): Array<InlayPresentationEntry> {
    return entries
  }

  override fun getMouseArea(pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage): InlayMouseArea? {
    val entry = findEntryByPoint(fontMetricsStorage, pointInsideInlay) ?: return null
    return entry.clickArea
  }
}

private val MARGIN_PADDING_BY_FORMAT = enumMapOf<HintMarginPadding, Pair<Int, Int>>().apply {
  put(HintMarginPadding.OnlyPadding, 0 to 7)
  put(HintMarginPadding.MarginAndSmallerPadding, 2 to 6)
}
private const val ARC_WIDTH = 8
private const val ARC_HEIGHT = 8
private const val BACKGROUND_ALPHA: Float = 0.55f
