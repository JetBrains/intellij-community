// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.DiffContext
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.AlignableChange.Companion.getAlignedChangeColor
import com.intellij.diff.tools.simple.AlignableChange.Companion.isSame
import com.intellij.diff.tools.simple.AlignedDiffModelBase.ChangeIntersection.*
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.impl.InlayKeys.ID_BEFORE_DISPOSAL
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vcs.ex.end
import com.intellij.openapi.vcs.ex.start
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class SimpleAlignedDiffModel(val viewer: SimpleDiffViewer)
  : AlignedDiffModelBase(viewer.request, viewer.context, viewer.editor1, viewer.editor2, viewer.syncScrollable) {
  init {
    textSettings.addListener(object : TextDiffSettingsHolder.TextDiffSettings.Listener {
      override fun alignModeChanged() {
        viewer.rediff()
      }
    }, this)
  }

  override fun getDiffChanges(): List<AlignableChange> {
    return viewer.myModel.allChanges
  }
}

interface AlignedDiffModel : Disposable {
  fun getDiffChanges(): List<AlignableChange>
  fun needAlignChanges(): Boolean
  fun realignChanges()
  fun clear()

  companion object {
    fun createSimpleAlignModel(viewer: SimpleDiffViewer): AlignedDiffModel {
      //return SimpleAlignedDiffModel(viewer)
      return NewAlignedDiffModel(viewer, viewer.request, viewer.context)
    }
  }
}

interface AlignableChange {
  val diffType: TextDiffType
  fun getStartLine(side: Side): Int
  fun getEndLine(side: Side): Int

  companion object {
    fun AlignableChange.isSame(other: AlignableChange) =
      getStartLine(Side.LEFT) == other.getStartLine(Side.LEFT) && getEndLine(Side.LEFT) == other.getEndLine(Side.LEFT) &&
      getStartLine(Side.RIGHT) == other.getStartLine(Side.RIGHT) && getEndLine(Side.RIGHT) == other.getEndLine(Side.RIGHT)

    fun getAlignedChangeColor(type: TextDiffType, editor: Editor): Color? {
      if (type === TextDiffType.MODIFIED) return null
      return ColorUtil.toAlpha(type.getColor(editor), 200)
    }
  }
}

abstract class AlignedDiffModelBase(val diffRequest: DiffRequest,
                                    val diffContext: DiffContext,
                                    val editor1: EditorEx,
                                    val editor2: EditorEx,
                                    val syncScrollable: SyncScrollSupport.SyncScrollable) : AlignedDiffModel {
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
    editor1.inlayModel.addListener(inlayListener, this)
    editor2.inlayModel.addListener(inlayListener, this)

    val softWrapListener = MySoftWrapModelListener()
    editor1.softWrapModel.addSoftWrapChangeListener(softWrapListener)
    editor2.softWrapModel.addSoftWrapChangeListener(softWrapListener)
  }

  override fun dispose() {
  }

  override fun needAlignChanges(): Boolean {
    val forcedValue: Boolean? = diffRequest.getUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF)
    if (forcedValue != null) return forcedValue

    return textSettings.isEnableAligningChangesMode
  }

  private fun alignChange(change: AlignableChange) {
    if (!needAlignChanges()) return

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

  private fun addInlay(change: AlignableChange, diffType: TextDiffType, inlaySide: Side) {
    val changeSide = inlaySide.other()

    val changeStartLine = change.getStartLine(changeSide)
    val changeEndLine = change.getEndLine(changeSide)
    val inlayStartLine = change.getStartLine(inlaySide)
    val inlayEndLine = change.getEndLine(inlaySide)
    val isLastLine = changeEndLine == DiffUtil.getLineCount(getEditor(changeSide).document)
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
    val editor = getEditor(side)
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
                               change: AlignableChange,
                               linesToAdd: Int,
                               inlaySideSoftWraps: Int,
                               isLastLineToAdd: Boolean): Inlay<ChangeAlignDiffInlayPresentation> {
    val editor = getEditor(side)
    val line = if (change.diffType === TextDiffType.MODIFIED) change.getEndLine(side) else change.getStartLine(side)
    val offset = DiffUtil.getOffset(editor.document, line, 0)

    val height = calculateHeight(side, change, linesToAdd, inlaySideSoftWraps)
    val inlayPresentation = ChangeAlignDiffInlayPresentation(editor, height, change.diffType)

    return editor.inlayModel
      .addBlockElement(offset,
                       InlayProperties()
                         .showAbove(!isLastLineToAdd)
                         .priority(ALIGNED_CHANGE_INLAY_PRIORITY),
                       inlayPresentation)!!
  }

  private fun calculateHeight(side: Side, change: AlignableChange, linesToAdd: Int, inlaySideSoftWraps: Int): Int {
    val inlayEditor = getEditor(side)
    val changeSide = side.other()
    val changeEditor = getEditor(changeSide)
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

  override fun realignChanges() {
    if (listOf(editor1, editor2).any { it.isDisposed || (it.foldingModel as FoldingModelImpl).isInBatchFoldingOperation }) return

    RecursionManager.doPreventingRecursion(this, true) {
      clear()
      getDiffChanges().forEach(::alignChange)
    }
  }

  override fun clear() {
    alignedInlays.values.forEach(Disposer::dispose)
    alignedInlays.clear()
    for ((side, highlighters) in inlayHighlighters) {
      val markupModel = getEditor(side).markupModel
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
      if (!needAlignChanges()) return

      if (!textSettings.isUseSoftWraps) {
        // this would also be called in case if editor font size changed and provide more clean view for aligning inlays
        realignChanges()
      }
    }

    override fun recalculationEnds() {
      if (!needAlignChanges()) return

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
    if (!needAlignChanges()) return
    if (inlay.renderer is BaseAlignDiffInlayPresentation) return //skip self

    val inlayLine = inlay.logicalLine
    val inlaySide = if (getEditor(Side.LEFT) == inlay.editor) Side.LEFT else Side.RIGHT

    if (needSkipInlay(inlay, inlaySide)) return

    val alignSide = inlaySide.other()
    val isAboveInlay = inlay.properties.isShownAbove
    val lineToBeAligned = getRelatedLogicalLine(inlaySide, inlayLine, isAboveInlay)
    val changeIntersection = getChangeIntersection(inlaySide, inlayLine, isAboveInlay)
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
          is UnderChange -> {
            val changeHeightDelta = with(changeIntersection.change) { countHeight(alignSide) - countHeight(inlaySide) }
            addEmptyInlay(inlayId, lineToBeAligned, inlay.heightInPixels, changeHeightDelta < 0, Int.MAX_VALUE, parent = inlay)
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
    val inlayEditor = getEditor(inlaySide)
    val alignEditor = getEditor(alignSide)
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
                            color: Color = getEditor(inlayId.side.other()).backgroundColor, parent: Disposable) {
    val editor = getEditor(inlayId.side)
    val offset = DiffUtil.getOffset(editor.document, line, 0)
    val disposable = Disposable { emptyInlays.remove(inlayId)?.also(Disposer::dispose) }

    emptyInlays[inlayId] =
      editor.inlayModel
        .addBlockElement(offset,
                         InlayProperties().showAbove(above).priority(priority),
                         EmptyLineAlignDiffInlayPresentation(editor, height, color))!!
        .also { Disposer.register(parent, disposable) }
  }

  private fun getChangeIntersection(side: Side, logicalLine: Int, isAboveLine: Boolean): ChangeIntersection {
    for (change in getDiffChanges()) {
      when {
        change.isStartLine(side, logicalLine) && isAboveLine -> return AboveChange
        change.isStartLine(side, logicalLine) && change.countHeight(side) > 0 && !isAboveLine -> return InsideChange(change)
        change.isEndLine(side, logicalLine) && change.isChangedSide(side) && !isAboveLine -> return UnderChange(change)
        change.isMiddleLine(side, logicalLine) -> return InsideChange(change)
      }
    }

    return NoIntersection
  }

  private fun AlignableChange.countHeight(side: Side) = getEndLine(side) - 1 - getStartLine(side)
  private fun AlignableChange.isChangedSide(side: Side) = getStartLine(side) != getEndLine(side) - 1
  private fun AlignableChange.isStartLine(side: Side, logicalLine: Int) = getStartLine(side) == logicalLine
  private fun AlignableChange.isEndLine(side: Side, logicalLine: Int) = getEndLine(side) - 1 == logicalLine
  private fun AlignableChange.isMiddleLine(side: Side, logicalLine: Int) =
    getStartLine(side) < logicalLine && getEndLine(side) - 1 >= logicalLine

  private sealed class ChangeIntersection {
    class InsideChange(val change: AlignableChange) : ChangeIntersection()
    object AboveChange : ChangeIntersection()
    class UnderChange(val change: AlignableChange) : ChangeIntersection()
    object NoIntersection : ChangeIntersection()
  }

  private data class InlayHeight(val id: InlayId, var height: Int)
  private data class InlayId(val side: Side, val offset: Int, val id: Long)
  private data class SideAndChange(val side: Side, val change: AlignableChange) {
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
    val needAlignLastLine = logicalLine == DiffUtil.getLineCount(getEditor(side).document) - 1

    if (needAlignLastLine && !isAboveInlay) {
      // for last line and below added inlay, related line should be always the last line (if there is no change intersection)
      val alignSide = side.other()
      val lastLine = DiffUtil.getLineCount(getEditor(alignSide).document) - 1
      val changeIntersection = getChangeIntersection(alignSide, lastLine, false)
      if (changeIntersection == NoIntersection
          || changeIntersection is UnderChange) { // e.g. change with deleted last N lines
        return lastLine
      }
    }

    //TODO line can be transferred before some change.
    // E.g.: added N new lines, review comment inlay placed on the left side, on the next line after aligning inlay.
    // In result the transferred position will be before the add change which leads adding of empty aligning inlay inside that change.
    return syncScrollable.transfer(side, logicalLine)
  }

  private val Inlay<*>.onLastLine get() = DiffUtil.getLineCount(editor.document) - 1 == logicalLine
  private val Inlay<*>.logicalLine get() = editor.offsetToLogicalPosition(offset).line
  private val Inlay<*>.id
    get() = with(this as RangeMarkerEx) {
      if (isValid) id
      else getUserData(ID_BEFORE_DISPOSAL) ?: throw IllegalStateException("Cannot determine inlay id")
    }

  private fun getEditor(side: Side): EditorEx {
    return side.selectNotNull(editor1, editor2)
  }

  protected val textSettings get() = TextDiffViewerUtil.getTextSettings(diffContext)

  private fun AlignableChange.calculateDeltaHeight(): Int {
    val leftStartLine = getStartLine(Side.LEFT)
    val leftEndLine = getEndLine(Side.LEFT)
    val rightStartLine = getStartLine(Side.RIGHT)
    val rightEndLine = getEndLine(Side.RIGHT)

    val delta = (leftEndLine - leftStartLine) - (rightEndLine - rightStartLine)

    return abs(delta) * getEditor(Side.LEFT).lineHeight
  }

  private fun AlignableChange.countSoftWraps(side: Side): Int {
    val editor = getEditor(side)
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


  }
}

/**
 * WIP:
 *
 * Benefits of the new model:
 * * Alignment of one change never affects alignment of others (except foldings)
 *
 * Current problems:
 * * Gutter's painting is broken for added/removed chunks
 * * When changes follow one by one ("Highlight split changes"-mode), some inlays may be mixed up. The root of the problem is that
 *   several inlays with the same priority added to the same offset
 * * Mirror inlays positioning works not so good
 */
private class NewAlignedDiffModel(private val viewer: SimpleDiffViewer,
                                  private val diffRequest: DiffRequest,
                                  private val diffContext: DiffContext) : AlignedDiffModel {
  private val queue = MergingUpdateQueue("SimpleAlignedDiffModel", 300, true, viewer.component, viewer)

  private val editor1 get() = viewer.editor1
  private val editor2 get() = viewer.editor2

  private val textSettings get() = TextDiffViewerUtil.getTextSettings(diffContext)

  // sorted by highlighter offset
  private val changeInlays = mutableListOf<ChangeInlay>()
  private val mirrorInlays1 = mutableListOf<MirrorInlay>()
  private val mirrorInlays2 = mutableListOf<MirrorInlay>()

  private val rangeHighlighters1 = mutableListOf<RangeHighlighter>()
  private val rangeHighlighters2 = mutableListOf<RangeHighlighter>()

  init {
    val inlayListener = MyInlayModelListener()
    editor1.inlayModel.addListener(inlayListener, viewer)
    editor2.inlayModel.addListener(inlayListener, viewer)

    val softWrapListener = MySoftWrapModelListener()
    editor1.softWrapModel.addSoftWrapChangeListener(softWrapListener)
    editor2.softWrapModel.addSoftWrapChangeListener(softWrapListener)
  }

  fun scheduleRealignChanges() {
    if (!needAlignChanges()) return
    queue.queue(Update.create("update") { realignChanges() })
  }

  override fun getDiffChanges(): List<AlignableChange> = viewer.myModel.allChanges

  override fun needAlignChanges(): Boolean {
    val forcedValue: Boolean? = diffRequest.getUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF)
    if (forcedValue != null) return forcedValue

    return textSettings.isEnableAligningChangesMode
  }

  override fun realignChanges() {
    if (!needAlignChanges()) return
    if (viewer.editors.any { it.isDisposed || (it.foldingModel as FoldingModelImpl).isInBatchFoldingOperation }) return

    RecursionManager.doPreventingRecursion(this, true) {
      clear()

      initInlays()
      updateInlayHeights()
    }
  }

  private fun calcPriority(isLastLine: Boolean, isTop: Boolean): Int {
    return if (isLastLine) {
             if (isTop) Int.MAX_VALUE else Int.MAX_VALUE - 1
           }
           else {
             if (isTop) Int.MAX_VALUE - 1 else Int.MAX_VALUE
           }
  }

  private fun initInlays() {
    val map1 = TreeMap<Int, ChangeInlay>()
    val map2 = TreeMap<Int, ChangeInlay>()

    for (change in viewer.diffChanges) {
      val range = change.range

      val changeInlay = ChangeInlay(
        change,
        createAlignInlay(Side.LEFT, range.start1, true, change.diffType),
        createAlignInlay(Side.RIGHT, range.start2, true, change.diffType),
        createAlignInlay(Side.LEFT, range.end1, false, change.diffType),
        createAlignInlay(Side.RIGHT, range.end2, false, change.diffType)
      )

      createGutterHighlighterIfNeeded(change.diffType, Side.LEFT, changeInlay.bottomInlay1)
      createGutterHighlighterIfNeeded(change.diffType, Side.RIGHT, changeInlay.bottomInlay2)

      changeInlays += changeInlay
      map1[range.start1] = changeInlay
      map2[range.start2] = changeInlay
    }

    val inlays1 = editor1.inlayModel.getBlockElementsInRange(0, editor1.document.textLength)
      .filter { it.renderer !is AlignDiffInlayRenderer }
    for (sourceInlay in inlays1) {
      val targetLine = getMirrorTargetLine(Side.LEFT, sourceInlay)
      val changeBlock = getChangeBlockFor(Side.LEFT, map1, targetLine)
      if (changeBlock != null) {
        changeBlock.innerSourceInlay1 += sourceInlay
      }
      else {
        mirrorInlays1 += MirrorInlay(
          sourceInlay,
          createMirrorInlay(Side.LEFT, sourceInlay, targetLine)
        )
      }
    }

    val inlays2 = editor2.inlayModel.getBlockElementsInRange(0, editor2.document.textLength)
      .filter { it.renderer !is AlignDiffInlayRenderer }
    for (sourceInlay in inlays2) {
      val targetLine = getMirrorTargetLine(Side.RIGHT, sourceInlay)
      val changeBlock = getChangeBlockFor(Side.RIGHT, map2, targetLine)
      if (changeBlock != null) {
        changeBlock.innerSourceInlay2 += sourceInlay
      }
      else {
        mirrorInlays2 += MirrorInlay(
          sourceInlay,
          createMirrorInlay(Side.RIGHT, sourceInlay, targetLine)
        )
      }
    }
  }

  private fun createAlignInlay(side: Side, line: Int, isTop: Boolean, diffType: TextDiffType): Inlay<AlignDiffInlayRenderer> {
    val editor = viewer.getEditor(side)
    val isLastLine = line == DiffUtil.getLineCount(editor.document)
    val offset = DiffUtil.getOffset(editor.document, line, 0)

    val properties = InlayProperties()
      .showAbove(!isLastLine)
      .priority(calcPriority(isLastLine, isTop))

    val color = if (isTop) null else getAlignedChangeColor(diffType, editor)
    val inlayPresentation = AlignDiffInlayRenderer(editor, color)

    return editor.inlayModel.addBlockElement(offset, properties, inlayPresentation)!!
  }

  private fun createGutterHighlighterIfNeeded(diffType: TextDiffType,
                                              side: Side,
                                              inlay: Inlay<AlignDiffInlayRenderer>) {
    if (diffType == TextDiffType.MODIFIED) return
    val highlighter = viewer.getEditor(side).markupModel
      .addRangeHighlighter(inlay.offset, inlay.offset, HighlighterLayer.SELECTION, TextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    highlighter.lineMarkerRenderer = DiffInlayGutterMarkerRenderer(diffType, inlay)
    if (side == Side.LEFT) {
      rangeHighlighters1 += highlighter
    } else {
      rangeHighlighters2 += highlighter
    }
  }

  private fun createMirrorInlay(sourceSide: Side,
                                sourceInlay: Inlay<*>,
                                targetLine: Int): Inlay<AlignDiffInlayRenderer>? {
    val side = sourceSide.other()
    val editor = viewer.getEditor(side)
    val offset = DiffUtil.getOffset(editor.document, targetLine, 0)

    val inlayProperties = InlayProperties()
      .showAbove(sourceInlay.properties.isShownAbove)
      .priority(sourceInlay.properties.priority)

    val inlayPresentation = AlignDiffInlayRenderer(editor, null)

    return editor.inlayModel.addBlockElement(offset, inlayProperties, inlayPresentation)
  }

  private fun getMirrorTargetLine(sourceSide: Side, sourceInlay: Inlay<*>): Int {
    val sourceEditor = viewer.getEditor(sourceSide)

    val sourceLine = sourceEditor.offsetToLogicalPosition(sourceInlay.offset).line
    return viewer.transferPosition(sourceSide, LineCol(sourceLine)).line
  }

  private fun getChangeBlockFor(sourceSide: Side, changeBlockMap: TreeMap<Int, ChangeInlay>, line: Int): ChangeInlay? {
    val block = changeBlockMap.ceilingEntry(line)?.value ?: return null
    val range = block.change.range
    if (range.start(sourceSide) <= line && range.end(sourceSide) > line) {
      return block
    }
    return null
  }

  private fun updateInlayHeights() {
    var last1 = 0
    var last2 = 0
    for (changeInlay in changeInlays) {
      val range = changeInlay.change.range

      val prefixDelta = calcSoftWrapsHeight(editor2, last2, range.start2) - calcSoftWrapsHeight(editor1, last1, range.start1)

      if (prefixDelta >= 0) {
        changeInlay.setTop(prefixDelta, 0)
      }
      else {
        changeInlay.setTop(0, -prefixDelta)
      }

      val bodyDelta = (range.end2 - range.start2) * editor2.lineHeight -
                      (range.end1 - range.start1) * editor1.lineHeight +
                      calcSoftWrapsHeight(editor2, range.start2, range.end2) -
                      calcSoftWrapsHeight(editor1, range.start1, range.end1) +
                      changeInlay.innerSourceInlay2.sumOf { it.heightInPixels } -
                      changeInlay.innerSourceInlay1.sumOf { it.heightInPixels }
      if (bodyDelta >= 0) {
        changeInlay.setBottom(bodyDelta, 0)
      }
      else {
        changeInlay.setBottom(0, -bodyDelta)
      }

      last1 = range.end1
      last2 = range.end2
    }

    for (mirrorInlay in mirrorInlays1) {
      val inlay = mirrorInlay.inlay ?: continue
      inlay.renderer.height = mirrorInlay.sourceInlay.heightInPixels
      inlay.update()
    }
    for (mirrorInlay in mirrorInlays2) {
      val inlay = mirrorInlay.inlay ?: continue
      inlay.renderer.height = mirrorInlay.sourceInlay.heightInPixels
      inlay.update()
    }
  }

  private fun calcSoftWrapsHeight(editor: Editor, line1: Int, line2: Int): Int {
    val softWrapModel = editor.softWrapModel
    var count = 0

    val range = DiffUtil.getLinesRange(editor.document, line1, line2)
    for (softWrap in softWrapModel.getSoftWrapsForRange(range.startOffset, range.endOffset)) {
      if (softWrapModel.isVisible(softWrap)) {
        count++
      }
    }
    return editor.lineHeight * count
  }
  override fun dispose() {
  }

  override fun clear() {
    disposeAndClear(changeInlays)
    disposeAndClear(mirrorInlays1)
    disposeAndClear(mirrorInlays2)

    for (highlighter in rangeHighlighters1) {
      editor1.markupModel.removeHighlighter(highlighter)
    }
    rangeHighlighters1.clear()

    for (highlighter in rangeHighlighters2) {
      editor2.markupModel.removeHighlighter(highlighter)
    }
    rangeHighlighters2.clear()
  }

  private fun disposeAndClear(disposables: MutableCollection<out Disposable>) {
    for (item in disposables) {
      Disposer.dispose(item)
    }
    disposables.clear()
  }

  private val SimpleDiffChange.range: Range
    get() = Range(
      getStartLine(Side.LEFT),
      getEndLine(Side.LEFT),
      getStartLine(Side.RIGHT),
      getEndLine(Side.RIGHT)
    )

  private inner class MySoftWrapModelListener : SoftWrapChangeListener {
    override fun softWrapsChanged() {
      if (!textSettings.isUseSoftWraps) {
        // this would also be called in case if editor font size changed and provide more clean view for aligning inlays
        scheduleRealignChanges()
      }
    }

    override fun recalculationEnds() {
      scheduleRealignChanges()
    }
  }

  private inner class MyInlayModelListener : InlayModel.Listener {
    override fun onAdded(inlay: Inlay<*>) {
      if (inlay.renderer is AlignDiffInlayRenderer) return
      scheduleRealignChanges()
    }

    override fun onRemoved(inlay: Inlay<*>) {
      if (inlay.renderer is AlignDiffInlayRenderer) return
      scheduleRealignChanges()
    }

    override fun onUpdated(inlay: Inlay<*>, changeFlags: Int) {
      if (inlay.renderer is AlignDiffInlayRenderer) return
      if (changeFlags and InlayModel.ChangeFlags.HEIGHT_CHANGED != 0) {
        scheduleRealignChanges()
      }
    }
  }

  private class AlignDiffInlayRenderer(
    private val editor: EditorEx,
    private val inlayColor: Color? = null
  ) : EditorCustomElementRenderer {
    var height: Int = 0

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
      val paintColor = inlayColor ?: return
      g.color = paintColor
      g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = max((editor as EditorImpl).preferredSize.width, editor.component.width)
    override fun calcHeightInPixels(inlay: Inlay<*>): Int = height
  }

  private class ChangeInlay(
    val change: SimpleDiffChange,

    val topInlay1: Inlay<AlignDiffInlayRenderer>,
    val topInlay2: Inlay<AlignDiffInlayRenderer>,

    val bottomInlay1: Inlay<AlignDiffInlayRenderer>,
    val bottomInlay2: Inlay<AlignDiffInlayRenderer>,
  ) : Disposable {
    val innerSourceInlay1 = mutableListOf<Inlay<*>>()
    val innerSourceInlay2 = mutableListOf<Inlay<*>>()

    fun setTop(height1: Int, height2: Int) {
      topInlay1.renderer.height = height1
      topInlay1.update()

      topInlay2.renderer.height = height2
      topInlay2.update()
    }

    fun setBottom(height1: Int, height2: Int) {
      bottomInlay1.renderer.height = height1
      bottomInlay1.update()

      bottomInlay2.renderer.height = height2
      bottomInlay2.update()
    }

    override fun dispose() {
      Disposer.dispose(topInlay1)
      Disposer.dispose(topInlay2)
      Disposer.dispose(bottomInlay1)
      Disposer.dispose(bottomInlay2)
    }
  }

  private class MirrorInlay(
    val sourceInlay: Inlay<*>,
    val inlay: Inlay<AlignDiffInlayRenderer>?
  ) : Disposable {
    override fun dispose() {
      if (inlay != null) Disposer.dispose(inlay)
    }
  }

  private class DiffInlayGutterMarkerRenderer(
    private val type: TextDiffType,
    private val inlay: Inlay<*>,
  ) : LineMarkerRendererEx {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      editor as EditorEx
      g as Graphics2D
      if (inlay is RangeMarker) {
        val gutter = editor.gutterComponentEx

        val inlayHeight = inlay.heightInPixels

        val preservedBackground = g.background
        val y = inlay.bounds?.y ?: return
        g.color = getAlignedChangeColor(type, editor)
        g.fillRect(0, y, gutter.width, inlayHeight)
        g.color = preservedBackground
      }
    }

    override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.CUSTOM
  }
}