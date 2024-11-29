// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.LightweightHint
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.CharArrayUtil
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

/**
 * Indents the hint to match the indent of the line given by the offset of the carrying inlay
 *
 * Concerns:
 * - Calculating the width of the indent requires a read action during rendering.
 * More correct approach would be to calculate and update it in [com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPass],
 * however, that creates a noticeable delay between when, e.g., user increases the indent of the line and the time the indent of the hint
 * updates.
 * - [Inlay.getOffset] will not return the correct value during inlay construction,
 * which is when `calcWidthInPixels` will be called for the first time. `initialIndentAnchorOffset` is used as a work-around.
 */
internal class IndentedDeclarativeHintView<View, Model>(val view: View, private val initialIndentAnchorOffset: Int)
  : DeclarativeHintView<Model>
  where View : DeclarativeHintView<Model> {

  lateinit var inlay: Inlay<*>

  override fun updateModel(newModel: Model) {
    view.updateModel(newModel)
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

  override fun calcWidthInPixels(inlay: Inlay<*>, fontMetricsStorage: InlayTextMetricsStorage): Int {
    return getViewIndentMargin(inlay) + view.calcWidthInPixels(inlay, fontMetricsStorage)
  }

  override fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
    fontMetricsStorage: InlayTextMetricsStorage,
  ) {
    view.paint(inlay, g, targetRegion.toViewRectangle(), textAttributes, fontMetricsStorage)
  }

  override fun handleLeftClick(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
    controlDown: Boolean,
  ) {
    val translated = pointInsideInlay.toPointInsideViewOrNull() ?: return
    view.handleLeftClick(e, translated, fontMetricsStorage, controlDown)
  }

  override fun handleHover(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  ): LightweightHint? {
    val translated = pointInsideInlay.toPointInsideViewOrNull() ?: return null
    return view.handleHover(e, translated, fontMetricsStorage)
  }

  override fun handleRightClick(e: EditorMouseEvent, pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage) {
    val translated = pointInsideInlay.toPointInsideViewOrNull() ?: return
    return view.handleRightClick(e, translated, fontMetricsStorage)
  }

  override fun getMouseArea(pointInsideInlay: Point, fontMetricsStorage: InlayTextMetricsStorage): InlayMouseArea? {
    val translated = pointInsideInlay.toPointInsideViewOrNull() ?: return null
    return view.getMouseArea(translated, fontMetricsStorage)
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

  companion object {
    internal fun calcIndentAnchorOffset(offset: Int, document: Document): Pair<Int, Int> {
      val lineStartOffset = DocumentUtil.getLineStartOffset(offset, document)
      val textStartOffset = CharArrayUtil.shiftForward(document.immutableCharSequence, lineStartOffset, " \t")
      return lineStartOffset to textStartOffset
    }
  }
}

@RequiresReadLock
private fun calcViewIndentMargin(offset: Int, editor: Editor): Int {
  val document = editor.document
  val text = document.immutableCharSequence
  val (lineStartOffset, textStartOffset) = IndentedDeclarativeHintView.calcIndentAnchorOffset(offset, document)
  val indentMargin = if (editor.inlayModel.isInBatchMode) {
    // avoid coordinate transformations in batch mode
    measureIndentSafely(text, lineStartOffset, textStartOffset, editor)
  }
  else {
    editor.offsetToXY(textStartOffset, true, false).x
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
