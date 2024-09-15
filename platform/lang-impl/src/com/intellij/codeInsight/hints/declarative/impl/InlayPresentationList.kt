// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintMarginPadding
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.impl.util.TinyTree
import com.intellij.codeInsight.hints.declarative.impl.views.DeclarativeHintView
import com.intellij.codeInsight.hints.presentation.InlayTextMetrics
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.psi.PsiDocumentManager
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
  @ApiStatus.Internal var model: InlayData,
) : DeclarativeHintView {
  private var entries: Array<InlayPresentationEntry> = model.tree.buildPresentationEntries()
  private var _partialWidthSums: IntArray? = null
  private var size: Float = Float.MAX_VALUE
  private var fontName: String = ""

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

  private fun getPartialWidthSums(storage: InlayTextMetricsStorage): IntArray {
    val sums = _partialWidthSums
    val metrics = getMetrics(storage)
    val isActual = metrics.isActual(size, fontName)
    if (!isActual || sums == null) {
      val computed = computePartialSums(metrics)
      _partialWidthSums = computed
      size = metrics.font.size2D
      fontName = metrics.font.family
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
    val project = e.editor.project ?: return
    val document = e.editor.document
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return
    val providerId = model.providerId
    val providerInfo = InlayHintsProviderFactory.getProviderInfo(psiFile.language, providerId) ?: return
    val providerName = providerInfo.providerName

    val inlayMenu: AnAction = ActionManager.getInstance().getAction("InlayMenu")
    val inlayMenuActionGroup = inlayMenu as ActionGroup
    val popupMenu = ActionManager.getInstance().createActionPopupMenu("InlayMenuPopup", inlayMenuActionGroup)
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(CommonDataKeys.PSI_FILE, psiFile)
      .add(CommonDataKeys.EDITOR, e.editor)
      .add(InlayHintsProvider.PROVIDER_ID, providerId)
      .add(InlayHintsProvider.PROVIDER_NAME, providerName)
      .add(InlayHintsProvider.INLAY_PAYLOADS, model.payloads?.associate { it.payloadName to it.payload })
      .build()
    popupMenu.setDataContext {
      dataContext
    }

    JBPopupMenu.showByEvent(e.mouseEvent, popupMenu.component)
  }

  private val marginAndPadding: Pair<Int, Int> get() = MARGIN_PADDING_BY_FORMAT[model.hintFormat.horizontalMarginPadding]!!
  private fun getTextWidth(storage: InlayTextMetricsStorage): Int = getPartialWidthSums(storage).lastOrNull() ?: 0
  private fun getBoxWidth(storage: InlayTextMetricsStorage): Int {
    val (_, padding) = marginAndPadding
    return 2 * padding + getTextWidth(storage)
  }

  private fun getFullWidth(storage: InlayTextMetricsStorage): Int {
    val (margin, padding) = marginAndPadding
    return 2 * (margin + padding) + getTextWidth(storage)
  }

  private fun findEntryByPoint(fontMetricsStorage: InlayTextMetricsStorage, pointInsideInlay: Point): InlayPresentationEntry? {
    val partialWidthSums = getPartialWidthSums(fontMetricsStorage)
    val (margin, padding) = marginAndPadding
    val initialLeftBound = margin + padding
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
  override fun calcWidthInPixels(inlay: Inlay<*>, textMetricsStorage: InlayTextMetricsStorage): Int = getFullWidth(textMetricsStorage)

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
    val (margin, padding) = marginAndPadding
    g.withTranslated(targetRegion.x, targetRegion.y) {
      if (hintFormat.colorKind.hasBackground()) {
        val rectHeight = targetRegion.height.toInt() - gap * 2
        val config = GraphicsUtil.setupAAPainting(g)
        GraphicsUtil.paintWithAlpha(g, BACKGROUND_ALPHA)
        g.color = attrs.backgroundColor ?: textAttributes.backgroundColor
        g.fillRoundRect(margin, gap, getBoxWidth(storage), rectHeight, ARC_WIDTH, ARC_HEIGHT)
        config.restore()
      }
    }


    g.withTranslated(margin + padding + targetRegion.x, targetRegion.y) {
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

  @ApiStatus.Internal
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