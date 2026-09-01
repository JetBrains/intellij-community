// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.codeInsight.daemon.GutterMark
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.marker.DefaultMarkerPolicy
import com.intellij.openapi.editor.impl.marker.MarkerSpec
import com.intellij.openapi.editor.impl.marker.PMarkerRoot
import com.intellij.openapi.editor.impl.marker.PersistentHighlighterPolicy
import com.intellij.openapi.editor.impl.marker.SnapshotMarkerEngineImpl
import com.intellij.openapi.editor.impl.marker.SnapshotRangeMarkerImpl
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.LineSeparatorRenderer
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.SeparatorPlacement
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.Consumer
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import java.util.concurrent.atomic.AtomicReference

internal class SnapshotRangeHighlighterImpl private constructor(
  private val storage: SnapshotHighlighterStorage,
  private val highlighterId: Long,
  startOffset: Int,
  endOffset: Int,
  private val layer: Int,
  private val targetArea: HighlighterTargetArea,
  initialSpec: MarkerSpec,
  private val persistent: Boolean,
  private var textAttributesKey: TextAttributesKey?,
) : SnapshotRangeMarkerImpl(
  storage.document,
  highlighterId,
  initialSpec,
  TextRange(startOffset, endOffset),
), RangeHighlighterEx {
  private var forcedTextAttributes: TextAttributes? = null
  private var lineMarkerRenderer: LineMarkerRenderer? = null
  private var errorStripeColor: Color? = null
  private var lineSeparatorColor: Color? = null
  private var separatorPlacement: SeparatorPlacement? = null
  private var gutterIconRenderer: GutterIconRenderer? = null

  @Volatile
  private var errorStripeTooltip: Any? = null

  private var editorFilter: MarkupEditorFilter = MarkupEditorFilter.EMPTY
  private var customRenderer: CustomHighlighterRenderer? = null
  private var lineSeparatorRenderer: LineSeparatorRenderer? = null
  private var visibleIfFolded: Boolean = false
  private var afterEndOfLine: Boolean = false
  private var thinErrorStripeMark: Boolean = false
  private var inBatchChange: Boolean = false
  private var batchChangeStatus: Int = 0

  override fun rootReference(snapshot: DocumentSnapshot): AtomicReference<PMarkerRoot> {
    return storage.rootReference(snapshot, targetArea)
  }

  override fun currentRootReference(): AtomicReference<PMarkerRoot> {
    return rootReference(storage.currentSnapshot())
  }

  override fun getLayer(): Int = layer

  override fun getTargetArea(): HighlighterTargetArea = targetArea

  override fun isPersistent(): Boolean = persistent

  override fun getFlavorFlags(): Byte {
    return ((if (getErrorStripeMarkColor(null) != null) RangeHighlighterTree.ERROR_STRIPE_FLAVOR_FLAG.toInt() else 0) or
            (if (isRenderedInGutter) RangeHighlighterTree.RENDER_IN_GUTTER_FLAVOR_FLAG.toInt() else 0)).toByte()
  }

  override fun getTextAttributesKey(): TextAttributesKey? = textAttributesKey

  override fun getForcedTextAttributes(): TextAttributes? = forcedTextAttributes

  override fun getForcedErrorStripeMarkColor(): Color? = errorStripeColor

  override fun getTextAttributes(scheme: EditorColorsScheme?): TextAttributes? {
    forcedTextAttributes?.let { return it }
    val key = textAttributesKey ?: return null
    val colorScheme = scheme ?: EditorColorsManager.getInstance().globalScheme
    return colorScheme.getAttributes(key)
  }

  override fun setTextAttributes(textAttributes: TextAttributes?) {
    storage.assertMayChange()
    val old = forcedTextAttributes
    if (old === textAttributes) return
    forcedTextAttributes = textAttributes

    val erasedChanged = old === TextAttributes.ERASE_MARKER || textAttributes === TextAttributes.ERASE_MARKER ||
                        old == null && textAttributesKey != null
    if (erasedChanged || old != textAttributes) {
      val fontStyleChanged = erasedChanged || fontStyle(old) != fontStyle(textAttributes)
      val foregroundColorChanged = erasedChanged || foregroundColor(old) != foregroundColor(textAttributes)
      fireChanged(false, fontStyleChanged, foregroundColorChanged)
    }
  }

  override fun setTextAttributesKey(textAttributesKey: TextAttributesKey) {
    storage.assertMayChange()
    val old = this.textAttributesKey
    this.textAttributesKey = textAttributesKey
    if (textAttributesKey != old) {
      fireChanged(false, forcedTextAttributes == null, forcedTextAttributes == null)
    }
  }

  override fun setVisibleIfFolded(value: Boolean) {
    storage.assertMayChange()
    visibleIfFolded = value
  }

  override fun isVisibleIfFolded(): Boolean = visibleIfFolded

  override fun getLineMarkerRenderer(): LineMarkerRenderer? = lineMarkerRenderer

  override fun setLineMarkerRenderer(renderer: LineMarkerRenderer?) {
    storage.assertMayChange()
    val old = lineMarkerRenderer
    lineMarkerRenderer = renderer
    if (old != renderer) fireChanged(renderersChanged = true, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getCustomRenderer(): CustomHighlighterRenderer? = customRenderer

  override fun setCustomRenderer(renderer: CustomHighlighterRenderer?) {
    storage.assertMayChange()
    val old = customRenderer
    customRenderer = renderer
    if (old != renderer) fireChanged(renderersChanged = true, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? = gutterIconRenderer

  override fun setGutterIconRenderer(renderer: GutterIconRenderer?) {
    storage.assertMayChange()
    val old: GutterMark? = gutterIconRenderer
    gutterIconRenderer = renderer
    if (old != renderer) {
      fireChanged(renderersChanged = true, fontStyleChanged = false, foregroundColorChanged = false)
      if (old is Disposable) Disposer.dispose(old)
    }
  }

  override fun getErrorStripeMarkColor(scheme: EditorColorsScheme?): Color? {
    if (errorStripeColor === NULL_COLOR) return null
    errorStripeColor?.let { return it }
    forcedTextAttributes?.let { return it.errorStripeColor }
    return getTextAttributes(scheme)?.errorStripeColor
  }

  override fun setErrorStripeMarkColor(color: Color?) {
    storage.assertMayChange()
    val newColor = color ?: NULL_COLOR
    val old = errorStripeColor
    errorStripeColor = newColor
    if (old != newColor) fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getErrorStripeTooltip(): Any? = errorStripeTooltip

  override fun setErrorStripeTooltip(tooltipObject: Any?) {
    storage.assertMayChange()
    val old = errorStripeTooltip
    errorStripeTooltip = tooltipObject
    if (old != tooltipObject) fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun isThinErrorStripeMark(): Boolean = thinErrorStripeMark

  override fun setThinErrorStripeMark(value: Boolean) {
    storage.assertMayChange()
    if (thinErrorStripeMark != value) {
      thinErrorStripeMark = value
      fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
    }
  }

  override fun getLineSeparatorColor(): Color? = lineSeparatorColor

  override fun setLineSeparatorColor(color: Color?) {
    storage.assertMayChange()
    val old = lineSeparatorColor
    lineSeparatorColor = color
    if (old != color) fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getLineSeparatorPlacement(): SeparatorPlacement? = separatorPlacement

  override fun setLineSeparatorPlacement(placement: SeparatorPlacement?) {
    storage.assertMayChange()
    val old = separatorPlacement
    separatorPlacement = placement
    if (old != placement) fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun setEditorFilter(filter: MarkupEditorFilter) {
    storage.assertMayChange()
    editorFilter = filter
    fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getEditorFilter(): MarkupEditorFilter = editorFilter

  override fun isAfterEndOfLine(): Boolean = afterEndOfLine

  override fun setAfterEndOfLine(afterEndOfLine: Boolean) {
    storage.assertMayChange()
    if (this.afterEndOfLine != afterEndOfLine) {
      this.afterEndOfLine = afterEndOfLine
      fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
    }
  }

  override fun setGreedyToLeft(greedy: Boolean) {
    storage.assertMayChange()
    if (isGreedyToLeft != greedy) {
      super.setGreedyToLeft(greedy)
      fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
    }
  }

  override fun setGreedyToRight(greedy: Boolean) {
    storage.assertMayChange()
    if (isGreedyToRight != greedy) {
      super.setGreedyToRight(greedy)
      fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
    }
  }

  override fun setStickingToRight(value: Boolean) {
    storage.assertMayChange()
    if (isStickingToRight() != value) {
      super<SnapshotRangeMarkerImpl>.setStickingToRight(value)
      fireChanged(renderersChanged = false, fontStyleChanged = false, foregroundColorChanged = false)
    }
  }

  override fun fireChanged(renderersChanged: Boolean, fontStyleChanged: Boolean, foregroundColorChanged: Boolean) {
    storage.assertMayChange()
    if (inBatchChange) {
      batchChangeStatus = batchChangeStatus or RangeHighlighterImpl.CHANGED_MASK.toInt()
      if (renderersChanged) batchChangeStatus = batchChangeStatus or RangeHighlighterImpl.RENDERERS_CHANGED_MASK.toInt()
      if (fontStyleChanged) batchChangeStatus = batchChangeStatus or RangeHighlighterImpl.FONT_STYLE_CHANGED_MASK.toInt()
      if (foregroundColorChanged) batchChangeStatus = batchChangeStatus or RangeHighlighterImpl.FOREGROUND_COLOR_CHANGED_MASK.toInt()
    }
    else {
      storage.fireAttributesChanged(this, renderersChanged, fontStyleChanged, foregroundColorChanged)
    }
    storage.updateFlavor(this)
  }

  override fun getAffectedAreaStartOffset(): Int {
    val startOffset = startOffset
    if (targetArea == HighlighterTargetArea.EXACT_RANGE) return startOffset
    val document = storage.document
    val textLength = document.textLength
    if (startOffset >= textLength) return textLength
    return document.getLineStartOffset(document.getLineNumber(startOffset))
  }

  override fun getAffectedAreaEndOffset(): Int {
    val endOffset = endOffset
    if (targetArea == HighlighterTargetArea.EXACT_RANGE) return endOffset
    val document = storage.document
    val textLength = document.textLength
    if (endOffset >= textLength) return endOffset
    return minOf(textLength, document.getLineEndOffset(document.getLineNumber(endOffset)) + 1)
  }

  @Synchronized
  @ApiStatus.Internal
  fun changeAttributesNoEvents(change: Consumer<in RangeHighlighterEx>): Byte {
    check(!inBatchChange)
    inBatchChange = true
    batchChangeStatus = 0
    return try {
      change.consume(this)
      batchChangeStatus.toByte()
    }
    finally {
      inBatchChange = false
      batchChangeStatus = 0
    }
  }

  override fun setLineSeparatorRenderer(renderer: LineSeparatorRenderer?) {
    storage.assertMayChange()
    val old = lineSeparatorRenderer
    lineSeparatorRenderer = renderer
    if (old != renderer) fireChanged(renderersChanged = true, fontStyleChanged = false, foregroundColorChanged = false)
  }

  override fun getLineSeparatorRenderer(): LineSeparatorRenderer? = lineSeparatorRenderer

  override fun beforeDispose() {
    storage.beforeRemoved(this)
  }

  override fun afterDispose() {
    storage.afterRemoved(this)
    val renderer = gutterIconRenderer
    if (renderer is Disposable) Disposer.dispose(renderer)
  }

  fun idForStorage(): Long = highlighterId

  fun targetAreaForStorage(): HighlighterTargetArea = targetArea

  fun updateFlavor() {
    storage.updateFlavor(this)
  }

  override fun toString(): String {
    return "RangeHighlighter: " +
           (if (isValid) "" else "(invalid)") +
           debugOffsets() +
           "; layer:" + layer +
           (if (errorStripeTooltip == null) "" else "; tooltip:$errorStripeTooltip") +
           (if (textAttributesKey == null) "" else "; textAttributeKey:$textAttributesKey")
  }

  companion object {
    @Suppress("InspectionUsingGrayColors", "UseJBColor")
    private val NULL_COLOR = Color(0, 0, 0)

    @JvmStatic
    fun create(
      storage: SnapshotHighlighterStorage,
      startOffset: Int,
      endOffset: Int,
      layer: Int,
      targetArea: HighlighterTargetArea,
      textAttributesKey: TextAttributesKey?,
      greedyToLeft: Boolean,
      greedyToRight: Boolean,
    ): SnapshotRangeHighlighterImpl {
      val spec = MarkerSpec(greedyToLeft, greedyToRight, policy = DefaultMarkerPolicy)
      return create(storage, startOffset, endOffset, layer, targetArea, textAttributesKey, spec, persistent = false)
    }

    @JvmStatic
    fun createPersistent(
      storage: SnapshotHighlighterStorage,
      offset: Int,
      layer: Int,
      targetArea: HighlighterTargetArea,
      textAttributesKey: TextAttributesKey?,
      normalizeStartOffset: Boolean,
    ): SnapshotRangeHighlighterImpl {
      val document = storage.document
      val line = document.getLineNumber(offset)
      val startOffset = if (normalizeStartOffset) document.getLineStartOffset(line) else offset
      val endOffset = document.getLineEndOffset(line)
      val policy = if (targetArea == HighlighterTargetArea.LINES_IN_RANGE) {
        PersistentHighlighterPolicy.LINES_IN_RANGE
      }
      else {
        PersistentHighlighterPolicy.EXACT_RANGE
      }
      val spec = MarkerSpec(false, false, policy = policy)
      return create(storage, startOffset, endOffset, layer, targetArea, textAttributesKey, spec, persistent = true)
    }

    private fun create(
      storage: SnapshotHighlighterStorage,
      startOffset: Int,
      endOffset: Int,
      layer: Int,
      targetArea: HighlighterTargetArea,
      textAttributesKey: TextAttributesKey?,
      spec: MarkerSpec,
      persistent: Boolean,
    ): SnapshotRangeHighlighterImpl {
      val highlighter = SnapshotRangeHighlighterImpl(
        storage,
        SnapshotMarkerEngineImpl.nextMarkerId(),
        startOffset,
        endOffset,
        layer,
        targetArea,
        spec,
        persistent,
        textAttributesKey,
      )
      storage.add(highlighter, startOffset, endOffset, spec)
      return highlighter
    }

    private fun fontStyle(attributes: TextAttributes?): Int = attributes?.fontType ?: Font.PLAIN

    private fun foregroundColor(attributes: TextAttributes?): Color? = attributes?.foregroundColor
  }
}
