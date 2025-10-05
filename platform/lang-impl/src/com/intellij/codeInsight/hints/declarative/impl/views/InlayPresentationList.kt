// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayTags
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.codeInsight.hints.presentation.scaleByFont
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.paint.EffectPainter
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.enumMapOf
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.withTranslated
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D
import kotlin.math.max

/**
 * @see com.intellij.codeInsight.hints.declarative.impl.PresentationTreeBuilderImpl
 */
@ApiStatus.Internal
class InlayPresentationList(
  model: InlayData,
) : InlayElementWithMarginsCompositeBase<InlayTextMetricsStorage, InlayPresentationEntry, InlayTextMetrics>(),
    InlayElementWithMargins<InlayTextMetricsStorage>,
    InlayTopLevelElement<InlayData> {
  var model: InlayData = model
    private set
  private var entries: Array<InlayPresentationEntry> = model.tree.buildPresentationEntries()
  private fun TinyTree<Any?>.buildPresentationEntries(): Array<InlayPresentationEntry> {
    return PresentationEntryBuilder(this, model.providerClass).buildPresentationEntries()
  }

  private val marginAndPadding: Pair<Int, Int> get() = MARGIN_PADDING_BY_FORMAT[model.hintFormat.horizontalMarginPadding]!!

  override fun computeLeftMargin(context: InlayTextMetricsStorage): Int = marginAndPadding.first

  override fun computeRightMargin(context: InlayTextMetricsStorage): Int = marginAndPadding.first

  private fun getPadding(context: InlayTextMetricsStorage): Int = marginAndPadding.second
  override fun computeBoxWidth(context: InlayTextMetricsStorage): Int {
    val entriesMetrics = getSubViewMetrics(context)
    return max(getPadding(context), entriesMetrics.leftMargin) +
           entriesMetrics.boxWidth +
           max(getPadding(context), entriesMetrics.rightMargin)
  }

  @RequiresEdt
  override fun updateModel(newModel: InlayData) {
    updateStateTree(newModel.tree, this.model.tree, 0, 0)
    this.model = newModel
    this.entries = newModel.tree.buildPresentationEntries()
    invalidate()
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

  override fun findEntryAtPoint(pointInsideInlay: Point, textMetricsStorage: InlayTextMetricsStorage): CapturedPointInfo {
    val entry = forSubViewAtPoint(pointInsideInlay, textMetricsStorage) { entry, _ ->
      entry
    }
    return CapturedPointInfo(this, entry)
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

  override fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
    textMetricsStorage: InlayTextMetricsStorage,
  ) {
    val editor = inlay.editor as EditorImpl
    val rectHeight = targetRegion.height.toInt()
    val rectWidth = targetRegion.width.toInt()
    val metrics = getMetrics(textMetricsStorage)
    val gap = if (rectHeight < metrics.lineHeight + 2) 1 else 2
    val hintFormat = model.hintFormat
    val attrKey = when (hintFormat.colorKind) {
      HintColorKind.Default -> DefaultLanguageHighlighterColors.INLAY_DEFAULT
      HintColorKind.Parameter -> DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT
      HintColorKind.TextWithoutBackground -> DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND
    }
    val attrs = editor.colorsScheme.getAttributes(attrKey)

    g.withTranslated(targetRegion.x, targetRegion.y) {
      if (hintFormat.colorKind.hasBackground()) {
        val rectHeight = rectHeight - gap * 2
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.color = attrs.backgroundColor ?: textAttributes.backgroundColor
        g.fillRoundRect(0, gap, computeBoxWidth(textMetricsStorage), rectHeight, scaleByFont(ARC_WIDTH, metrics.font.size2D), scaleByFont(ARC_HEIGHT, metrics.font.size2D))
        config.restore()
      }
    }

    g.withTranslated(getPadding(textMetricsStorage) + targetRegion.x, targetRegion.y) {
      forEachSubViewBounds(textMetricsStorage) { entry, leftBound, _ ->
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
        g.withTranslated(leftBound, 0) {
          entry.render(g, metrics, finalAttrs, model.disabled, gap, targetRegion.height.toInt(), editor)
        }
      }
      if (model.disabled) {
        val savedColor = g.color
        try {
          val effectColor = textAttributes.effectColor ?: textAttributes.foregroundColor ?: return@withTranslated
          g.color = effectColor
          EffectPainter.STRIKE_THROUGH.paint(g, 0, editor.ascent, rectWidth - 2 * getPadding(textMetricsStorage), metrics.ascent, metrics.font)
        }
        finally {
          g.color = savedColor
        }
      }
    }
  }

  private fun getMetrics(fontMetricsStorage: InlayTextMetricsStorage): InlayTextMetrics =
    fontMetricsStorage.getFontMetrics(small = model.hintFormat.fontSize == HintFontSize.ABitSmallerThanInEditor)

  @TestOnly
  fun getEntries(): Array<InlayPresentationEntry> {
    return entries
  }

  override fun getSubView(index: Int): InlayPresentationEntry = entries[index]

  override val subViewCount: Int
    get() = entries.size

  override fun computeSubViewContext(context: InlayTextMetricsStorage): InlayTextMetrics = getMetrics(context)
}

private val MARGIN_PADDING_BY_FORMAT = enumMapOf<HintMarginPadding, Pair<Int, Int>>().apply {
  put(HintMarginPadding.OnlyPadding, 0 to 7)
  put(HintMarginPadding.MarginAndSmallerPadding, 2 to 6)
}
private const val ARC_WIDTH = 8
private const val ARC_HEIGHT = 8
private const val BACKGROUND_ALPHA: Float = 0.55f
