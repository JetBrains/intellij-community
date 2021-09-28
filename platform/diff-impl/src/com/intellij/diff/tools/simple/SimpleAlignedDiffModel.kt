// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple

import com.intellij.diff.util.DiffInlayMarkerRenderer
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

class SimpleAlignedDiffModel(private val viewer: SimpleDiffViewer) {
  private val alignedInlays = mutableListOf<Inlay<AlignDiffInlayPresentation>>()
  private val inlayHighlighters = mutableMapOf<Side, MutableList<RangeHighlighter>>()

  fun alignChange(change: SimpleDiffChange) {
    if (!viewer.needAlignChanges()) return

    when (change.diffType) {
      TextDiffType.INSERTED -> {
        addInlay(change, TextDiffType.INSERTED, Side.LEFT)
      }
      TextDiffType.DELETED -> {
        addInlay(change, TextDiffType.DELETED, Side.RIGHT)
      }
      TextDiffType.MODIFIED -> {
        addInlay(change, TextDiffType.MODIFIED, Side.LEFT)
        addInlay(change, TextDiffType.MODIFIED, Side.RIGHT)
      }
    }
  }

  private fun addInlay(change: SimpleDiffChange, diffType: TextDiffType, inlaySide: Side) {
    val changeSide = inlaySide.other()

    val changeStartLine = change.getStartLine(changeSide)
    val changeEndLine = change.getEndLine(changeSide)
    val inlayStartLine = change.getStartLine(inlaySide)
    val inlayEndLine = change.getEndLine(inlaySide)
    val isLastLine = changeEndLine == DiffUtil.getLineCount(viewer.getEditor(changeSide).document)

    val delta = (changeEndLine - changeStartLine) - (inlayEndLine - inlayStartLine)
    if (delta <= 0) return

    createAlignInlay(inlaySide, change, delta, isLastLine)
      .also { createInlayHighlighter(inlaySide, it, diffType, isLastLine) }
      .apply(alignedInlays::add)
  }

  private fun createInlayHighlighter(side: Side, inlay: Inlay<*>, type: TextDiffType, isLastLine: Boolean) {
    val editor = viewer.getEditor(side)
    val startOffset = inlay.offset
    val endOffset = if (inlay is RangeMarker) inlay.endOffset else startOffset

    val highlighter = editor.markupModel
      .addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION, TextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    if (type != TextDiffType.MODIFIED) {
      highlighter.lineMarkerRenderer = DiffInlayMarkerRenderer(type, inlay, isLastLine)
    }
    inlayHighlighters.getOrPut(side) { mutableListOf() }.add(highlighter)
  }

  private fun createAlignInlay(side: Side,
                               change: SimpleDiffChange,
                               linesToAdd: Int,
                               isLastLineToAdd: Boolean): Inlay<AlignDiffInlayPresentation> {
    val editor = viewer.getEditor(side) as EditorImpl
    val offset = DiffUtil.getOffset(editor.document, change.getStartLine(side), 0)
    val inlayColor = getAlignedChangeColor(change.diffType, editor)
    val inlayPresentation = AlignDiffInlayPresentation(editor, linesToAdd, inlayColor)

    return editor.inlayModel.addBlockElement(offset, InlayProperties().showAbove(!isLastLineToAdd), inlayPresentation)!!
  }

  fun clear() {
    alignedInlays.forEach(Disposer::dispose)
    alignedInlays.clear()
    for ((side, highlighters) in inlayHighlighters) {
      val markupModel = viewer.getEditor(side).markupModel
      highlighters.forEach(markupModel::removeHighlighter)
    }
    inlayHighlighters.clear()
  }

  private class AlignDiffInlayPresentation(private val editor: EditorEx,
                                           private val linesToAdd: Int,
                                           private val inlayColor: Color? = null) : EditorCustomElementRenderer {

    val height get() = editor.lineHeight * linesToAdd
    val width get() = editor.component.width

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      editor as EditorImpl
      val paintColor = inlayColor ?: return

      g.color = paintColor
      g.fillRect(targetRegion.x, targetRegion.y, editor.preferredSize.width, height)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = height
  }

  companion object {
    fun getAlignedChangeColor(type: TextDiffType, editor: Editor): Color? {
      return if (type === TextDiffType.MODIFIED) null else type.getColor(editor).let { ColorUtil.toAlpha(it, 200) }
    }
  }
}
