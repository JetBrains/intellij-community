// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.simple

import com.intellij.diff.tools.simple.SimpleAlignedDiffModel.ChangeIntersection.*
import com.intellij.diff.util.*
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColorUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class SimpleAlignedDiffModel(private val viewer: SimpleDiffViewer) {
  /**
   * Changes mapped to corresponding change aligning inlays (INSERTED, DELETED, MODIFIED changes).
   */
  private val alignedInlays = mutableMapOf<SideAndChange, Inlay<ChangeAlignDiffInlayPresentation>>()

  /**
   * Inlay lines mapped to corresponding aligning empty line inlay from other diff side.
   */
  private val emptyInlays = mutableMapOf<InlayId, Inlay<EmptyLineAlignDiffInlayPresentation>>()
  private val adjustedInlaysHeights = mutableMapOf<SideAndChange, HashSet<InlayHeight>>()
  private val inlayHighlighters = mutableMapOf<Side, MutableList<RangeHighlighter>>()

  init {
    val inlayListener = MyInlayModelListener()
    if (editor1 !is EditorWindow) {
      editor1.inlayModel.addListener(inlayListener, viewer)
    }
    editor2.inlayModel.addListener(inlayListener, viewer)

    val softWrapListener = MySoftWrapModelListener()
    editor1.softWrapModel.addSoftWrapChangeListener(softWrapListener)
    editor2.softWrapModel.addSoftWrapChangeListener(softWrapListener)
  }

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
    val changeSideSoftWraps = change.countSoftWraps(changeSide)
    val inlaySideSoftWraps = change.countSoftWraps(inlaySide)
    val delta = (changeEndLine - changeStartLine) - (inlayEndLine - inlayStartLine)

    if (delta < 0) return
    if (delta == 0 && changeSideSoftWraps == inlaySideSoftWraps) return
    if (delta == 0 && inlaySideSoftWraps > changeSideSoftWraps) return

    createAlignInlay(inlaySide, change, delta, inlaySideSoftWraps, isLastLine)
      .also { createInlayHighlighter(inlaySide, it, diffType, isLastLine) }
      .also { alignedInlays[SideAndChange(inlaySide, change)] = it }
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
                               inlaySideSoftWraps: Int,
                               isLastLineToAdd: Boolean): Inlay<ChangeAlignDiffInlayPresentation> {
    val editor = viewer.getEditor(side)
    val line = if (change.diffType === TextDiffType.MODIFIED) change.getEndLine(side) else change.getStartLine(side)
    val offset = DiffUtil.getOffset(editor.document, line, 0)
    val inlayPresentation =
      ChangeAlignDiffInlayPresentation(editor,
        calculateHeight(side, change, linesToAdd, inlaySideSoftWraps), change.diffType)

    return editor.inlayModel
      .addBlockElement(offset,
        InlayProperties()
          .showAbove(!isLastLineToAdd)
          .priority(ALIGNED_CHANGE_INLAY_PRIORITY),
        inlayPresentation)!!
  }

  private fun calculateHeight(side: Side, change: SimpleDiffChange, linesToAdd: Int, inlaySideSoftWraps: Int): Int {
    val inlayEditor = viewer.getEditor(side)
    val changeSide = side.other()
    val changeEditor = viewer.getEditor(changeSide)
    var height = adjustedInlaysHeights[SideAndChange(side, change)]?.sumOf(InlayHeight::height) ?: 0

    height += linesToAdd * changeEditor.lineHeight

    for (line in change.getStartLine(changeSide) until change.getEndLine(changeSide)) {
      height += changeEditor.lineHeight * changeEditor.softWrapModel.getSoftWrapsForLine(line).size
    }

    if (change.diffType === TextDiffType.MODIFIED) {
      val inlaySoftWrapHeight = inlayEditor.lineHeight * inlaySideSoftWraps
      if (height > inlaySoftWrapHeight) {
        height -= inlayEditor.lineHeight * inlaySideSoftWraps
      }
    }

    return height
  }

  private fun realignChanges() {
    if (viewer.editors.any { (it.foldingModel as FoldingModelImpl).isInBatchFoldingOperation }) return

    clear()
    viewer.diffChanges.forEach(::alignChange)
  }

  fun clear() {
    alignedInlays.values.forEach(Disposer::dispose)
    alignedInlays.clear()
    for ((side, highlighters) in inlayHighlighters) {
      val markupModel = viewer.getEditor(side).markupModel
      highlighters.forEach(markupModel::removeHighlighter)
    }
    inlayHighlighters.clear()
  }

  private open class BaseAlignDiffInlayPresentation(private val editor: EditorEx,
                                                    var height: Int,
                                                    private val inlayColor: Color? = null) : EditorCustomElementRenderer {

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      editor as EditorImpl
      val paintColor = inlayColor ?: return

      g.color = paintColor
      g.fillRect(targetRegion.x, targetRegion.y, editor.maxWidth, height)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = editor.maxWidth

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = height

    private val EditorEx.maxWidth get() = max((this as EditorImpl).preferredSize.width, editor.component.width)
  }

  private class EmptyLineAlignDiffInlayPresentation(editor: EditorEx, height: Int, inlayColor: Color? = editor.backgroundColor) :
    BaseAlignDiffInlayPresentation(editor, height, inlayColor)

  private class ChangeAlignDiffInlayPresentation(editor: EditorEx, height: Int, diffType: TextDiffType) :
    BaseAlignDiffInlayPresentation(editor, height, getAlignedChangeColor(diffType, editor))

  private inner class MySoftWrapModelListener : SoftWrapChangeListener {
    override fun softWrapsChanged() {
      if (!viewer.needAlignChanges()) return

      if (!viewer.textSettings.isUseSoftWraps) {
        // this would also be called in case if editor font size changed and provide more clean view for aligning inlays
        realignChanges()
      }
    }

    override fun recalculationEnds() {
      if (!viewer.needAlignChanges()) return

      realignChanges()
    }
  }

  private inner class MyInlayModelListener : InlayModel.Listener {
    override fun onAdded(inlay: Inlay<*>) = processInlay(inlay, ProcessType.ADDED)

    override fun onRemoved(inlay: Inlay<*>) = processInlay(inlay, ProcessType.REMOVED)

    override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
      if (changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED != 0) {
        processInlay(inlay, ProcessType.HEIGHT_UPDATED)
      }
    }
  }

  private enum class ProcessType { ADDED, REMOVED, HEIGHT_UPDATED }

  private fun processInlay(inlay: Inlay<*>, processType: ProcessType) {
    if (!viewer.needAlignChanges()) return
    if (inlay.renderer is BaseAlignDiffInlayPresentation) return //skip self

    val inlayLine = inlay.logicalLine
    val inlaySide = if (viewer.getEditor(Side.LEFT) == inlay.editor) Side.LEFT else Side.RIGHT

    if (needSkipInlay(inlay, inlaySide)) return

    val alignSide = inlaySide.other()
    val isAboveInlay = inlay.properties.isShownAbove
    val lineToBeAligned = getRelatedLogicalLine(inlaySide, inlayLine, isAboveInlay)
    val changeIntersection = getChangeIntersection(inlaySide, inlayLine)
    val inlayId = InlayId(alignSide, inlay.offset, inlay.id)

    when (processType) {
      ProcessType.REMOVED -> {
        changeAlignedInlayHeight(changeIntersection, alignSide, inlay, processType)
        emptyInlays.remove(inlayId)?.let(Disposer::dispose)
      }
      ProcessType.HEIGHT_UPDATED -> {
        changeAlignedInlayHeight(changeIntersection, alignSide, inlay, processType)
        emptyInlays[inlayId]?.run { renderer.height = inlay.heightInPixels; update() }
      }
      ProcessType.ADDED -> {
        when (changeIntersection) {
          is AboveChange -> {
            val alignInlayPriority = if (isAboveInlay) ALIGNED_CHANGE_INLAY_PRIORITY else Int.MIN_VALUE
            addEmptyInlay(inlayId, lineToBeAligned, inlay.heightInPixels, isAboveInlay, alignInlayPriority, parent = inlay)
          }
          is InsideChange -> {
            changeAlignedInlayHeight(changeIntersection, alignSide, inlay, processType)
            val change = changeIntersection.change
            if (!alignedInlays.containsKey(SideAndChange(alignSide, change))) {
              val alignInlayPriority = if (isAboveInlay) ALIGNED_CHANGE_INLAY_PRIORITY else Int.MIN_VALUE
              val color = change.diffType.getColor(inlay.editor)
              addEmptyInlay(inlayId, lineToBeAligned, inlay.heightInPixels, isAboveInlay, alignInlayPriority, color, parent = inlay)
            }
          }
          is NoIntersection -> {
            addEmptyInlay(inlayId, lineToBeAligned, inlay.heightInPixels, isAboveInlay, Int.MAX_VALUE, parent = inlay)
          }
        }
      }
    }
  }

  private fun needSkipInlay(inlay: Inlay<*>,
                            inlaySide: Side): Boolean {
    val alignSide = inlaySide.other()
    val inlayEditor = viewer.getEditor(inlaySide)
    val alignEditor = viewer.getEditor(alignSide)
    //inlays added below line not supported, except last line
    return !inlay.properties.isShownAbove && inlay.onLastLine &&
           DiffUtil.getLineCount(alignEditor.document) <= DiffUtil.getLineCount(inlayEditor.document)
  }

  private fun changeAlignedInlayHeight(changeIntersection: ChangeIntersection, side: Side, inlay: Inlay<*>, processType: ProcessType) {
    if (changeIntersection !is InsideChange) return

    val change = changeIntersection.change
    val inlayHeight = inlay.heightInPixels
    val inlayId = InlayId(side, inlay.offset, inlay.id)

    val sideAndChange = SideAndChange(side, change)
    alignedInlays[sideAndChange]
      ?.run {
        val storedInlaysHeights = adjustedInlaysHeights.getOrPut(sideAndChange) { hashSetOf() }
        val storedInlayHeight = storedInlaysHeights.find { it.id == inlayId } ?: InlayHeight(inlayId, inlayHeight)

        when (processType) {
          ProcessType.REMOVED -> {
            storedInlaysHeights.removeIf { it.id == storedInlayHeight.id }
            renderer.height -= inlayHeight
          }
          ProcessType.ADDED -> {
            storedInlaysHeights.add(storedInlayHeight)
            renderer.height += inlayHeight
          }
          ProcessType.HEIGHT_UPDATED -> {
            storedInlayHeight.height = inlayHeight
            renderer.height = change.calculateDeltaHeight() + storedInlaysHeights.sumOf(InlayHeight::height)
          }
        }
        update()
      }
  }

  private fun addEmptyInlay(inlayId: InlayId, line: Int, height: Int, above: Boolean, priority: Int,
                            color: Color = viewer.getEditor(inlayId.side.other()).backgroundColor, parent: Disposable) {
    val editor = viewer.getEditor(inlayId.side)
    val offset = DiffUtil.getOffset(editor.document, line, 0)
    val disposable = Disposable { emptyInlays.remove(inlayId)?.also(Disposer::dispose) }

    emptyInlays[inlayId] =
      editor.inlayModel.addBlockElement(offset,
        InlayProperties().showAbove(above).priority(priority), EmptyLineAlignDiffInlayPresentation(editor, height, color))!!
        .also { Disposer.register(parent, disposable) }
  }

  private fun getChangeIntersection(side: Side, logicalLine: Int): ChangeIntersection {
    for (change in viewer.diffChanges) {
      when {
        change.isStartLine(side, logicalLine) -> return AboveChange
        change.isMiddleLine(side, logicalLine) -> return InsideChange(change)
      }
    }

    return NoIntersection
  }

  private fun SimpleDiffChange.isStartLine(side: Side, logicalLine: Int) = getStartLine(side) == logicalLine
  private fun SimpleDiffChange.isMiddleLine(side: Side, logicalLine: Int) =
    getStartLine(side) < logicalLine && getEndLine(side) - 1 >= logicalLine

  private sealed class ChangeIntersection {
    class InsideChange(val change: SimpleDiffChange) : ChangeIntersection()
    object AboveChange : ChangeIntersection()
    object NoIntersection : ChangeIntersection()
  }

  private data class InlayHeight(val id: InlayId, var height: Int)
  private data class InlayId(val side: Side, val offset: Int, val id: Long)
  private data class SideAndChange(val side: Side, val change: SimpleDiffChange) {
    override fun equals(other: Any?): Boolean {
      return other is SideAndChange && side == other.side && change.isSame(other.change)
    }

    override fun hashCode(): Int {
      return Objects.hash(side,
        change.getStartLine(Side.LEFT), change.getEndLine(Side.LEFT),
        change.getStartLine(Side.RIGHT), change.getEndLine(Side.RIGHT))
    }
  }

  private fun getRelatedLogicalLine(side: Side, logicalLine: Int, isAboveInlay: Boolean): Int {
    val needAlignLastLine = logicalLine == DiffUtil.getLineCount(viewer.getEditor(side).document) - 1

    if (needAlignLastLine && !isAboveInlay) {
      // for last line and below added inlay, related line should be always the last line (if there is no change intersection)
      val alignSide = side.other()
      val lastLine = DiffUtil.getLineCount(viewer.getEditor(alignSide).document) - 1
      val changeIntersection = getChangeIntersection(alignSide, lastLine)
      if (changeIntersection == NoIntersection) {
        return lastLine
      }
    }

    return viewer.transferPosition(side, LineCol(logicalLine, 0)).line
  }

  private val Inlay<*>.onLastLine get() = DiffUtil.getLineCount(editor.document) - 1 == logicalLine
  private val Inlay<*>.logicalLine get() = editor.offsetToLogicalPosition(offset).line
  private val Inlay<*>.id get() = (this as RangeMarkerEx).id

  private val editor1 get() = viewer.editor1
  private val editor2 get() = viewer.editor2

  private fun SimpleDiffChange.calculateDeltaHeight(): Int {
    val leftStartLine = getStartLine(Side.LEFT)
    val leftEndLine = getEndLine(Side.LEFT)
    val rightStartLine = getStartLine(Side.RIGHT)
    val rightEndLine = getEndLine(Side.RIGHT)

    val delta = (leftEndLine - leftStartLine) - (rightEndLine - rightStartLine)

    return abs(delta) * viewer.getEditor(Side.LEFT).lineHeight
  }

  private fun SimpleDiffChange.countSoftWraps(side: Side): Int {
    val editor = viewer.getEditor(side)
    val softWrapModel = editor.softWrapModel

    if (!softWrapModel.isSoftWrappingEnabled) return 0

    val document = editor.document
    val startOffset = document.getLineStartOffsetSafe(getStartLine(side))
    val endOffset = document.getLineEndOffsetSafe(getEndLine(side) - 1)
    return softWrapModel.getSoftWrapsForRange(startOffset, endOffset).size
  }

  private fun Document.getLineEndOffsetSafe(line: Int): Int {
    if (line < 0) return 0
    if (line >= DiffUtil.getLineCount(this)) return textLength

    return getLineEndOffset(line)
  }

  private fun Document.getLineStartOffsetSafe(line: Int): Int {
    if (line < 0) return 0
    if (line >= DiffUtil.getLineCount(this)) return textLength

    return getLineStartOffset(line)
  }

  companion object {
    const val ALIGNED_CHANGE_INLAY_PRIORITY = 0

    private fun SimpleDiffChange.isSame(other: SimpleDiffChange) =
      getStartLine(Side.LEFT) == other.getStartLine(Side.LEFT) && getEndLine(Side.LEFT) == other.getEndLine(Side.LEFT) &&
      getStartLine(Side.RIGHT) == other.getStartLine(Side.RIGHT) && getEndLine(Side.RIGHT) == other.getEndLine(Side.RIGHT)

    fun getAlignedChangeColor(type: TextDiffType, editor: Editor): Color? {
      return if (type === TextDiffType.MODIFIED) null else type.getColor(editor).let { ColorUtil.toAlpha(it, 200) }
    }
  }
}
