// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.inlayRenderer

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.views.CapturedPointInfo
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationComposite
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.formatting.visualLayer.VirtualFormattingInlaysInfo
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

/**
 * Indents the hint to match the indent of the line given by the offset of the carrying inlay
 *
 * Caveats:
 * - Calculating the width of the indent requires a read action during rendering.
 * More correct approach would be to calculate and update it in [com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass],
 * however, that creates a noticeable delay between when, e.g., user increases the indent of the line and the time the indent of the hint
 * updates.
 * - [Inlay.getOffset] will not return the correct value during inlay construction,
 * which is when `calcWidthInPixels` will be called for the first time. `initialIndentAnchorOffset` is used as a work-around.
 */
@ApiStatus.Internal
class DeclarativeIndentedBlockInlayRenderer(
  inlayData: List<InlayData>,
  fontMetricsStorage: InlayTextMetricsStorage,
  providerId: String,
  sourceId: String,
  private val initialIndentAnchorOffset: Int,
) : DeclarativeInlayRendererBase<List<InlayData>>(providerId, sourceId, fontMetricsStorage) {

  override val view = InlayPresentationComposite(inlayData)

  override val presentationLists: List<InlayPresentationList> get() = view.presentationLists

  override fun updateModel(newModel: List<InlayData>) {
    view.updateModel(newModel)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    super.paint(inlay, g, targetRegion.toViewRectangle(), textAttributes)
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    // ignore margins here:
    // * left-margin is subsumed by the indent margin (even if indent is 0, so that the edges are aligned),
    // * right-margin is not needed as there will be nothing displayed to the left of the block inlay
    return getViewIndentMargin(inlay) + view.getSubViewMetrics(textMetricsStorage).boxWidth
  }

  override fun capturePoint(pointInsideInlay: Point, textMetricsStorage: InlayTextMetricsStorage): CapturedPointInfo? {
    return super.capturePoint(pointInsideInlay.toPointInsideViewOrNull() ?: return null, textMetricsStorage)
  }

  private fun Point.toPointInsideViewOrNull(): Point? {
    val indentMargin = getViewIndentMargin()
    if (this.x < indentMargin) {
      return null
    }
    return Point(this.x - indentMargin, this.y)
  }

  private fun Rectangle2D.toViewRectangle(): Rectangle2D {
    val indentMargin = getViewIndentMargin()
    return Rectangle2D.Double(this.x + indentMargin,
                              this.y,
                              this.width - indentMargin,
                              this.height)
  }

  private fun getViewIndentMargin(inlay: Inlay<*>? = null): Int =
    if (this::inlay.isInitialized) {
      calcViewIndentMargin(this.inlay.offset, this.inlay.editor)
    }
    else if (inlay != null) {
      calcViewIndentMargin(initialIndentAnchorOffset, inlay.editor)
    }
    else {
      0
    }
}

@RequiresReadLock
private fun calcViewIndentMargin(offset: Int, editor: Editor): Int {
  val document = editor.document
  val text = document.immutableCharSequence
  val (lineStartOffset, textStartOffset) = calcIndentAnchorOffset(offset, document)
  val indentMargin = if (editor.inlayModel.isInBatchMode) {
    // avoid coordinate transformations in batch mode
    measureIndentSafely(text, lineStartOffset, textStartOffset, editor)
  }
  else {
    val vfmtRightShift = VirtualFormattingInlaysInfo.measureVirtualFormattingInlineInlays(editor, textStartOffset, textStartOffset)
    editor.offsetToXY(textStartOffset, false, false).x + vfmtRightShift
  }
  return indentMargin
}

private fun measureIndentSafely(text: CharSequence, start: Int, end: Int, editor: Editor): Int {
  val spaceWidth = EditorUtil.getPlainSpaceWidth(editor)
  val tabSize = EditorUtil.getTabSize(editor)
  var columns = 0
  var offset = start
  while (offset < end) {
    val c = text[offset++]
    when (c) {
      '\t' -> {
        columns += (columns / tabSize + 1) * tabSize
      }
      else -> {
        columns += 1
      }
    }
  }
  return columns * spaceWidth
}

private fun calcIndentAnchorOffset(offset: Int, document: Document): Pair<Int, Int> {
  val lineStartOffset = DocumentUtil.getLineStartOffset(offset, document)
  val textStartOffset = CharArrayUtil.shiftForward(document.immutableCharSequence, lineStartOffset, " \t")
  return lineStartOffset to textStartOffset
}