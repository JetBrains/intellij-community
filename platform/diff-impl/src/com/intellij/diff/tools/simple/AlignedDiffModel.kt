// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.simple

import com.intellij.diff.DiffContext
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.simple.AlignableChange.Companion.getAlignedChangeColor
import com.intellij.diff.tools.util.SyncScrollSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vcs.ex.end
import com.intellij.openapi.vcs.ex.start
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.util.*
import javax.swing.JComponent
import kotlin.math.max

@ApiStatus.Internal
class SimpleAlignedDiffModel(val viewer: SimpleDiffViewer): AlignedDiffModelBase(viewer.request, viewer.context,
                                                                                 viewer.component,
                                                                                 viewer.editor1, viewer.editor2,
                                                                                 viewer.syncScrollable) {
  init {
    textSettings.addListener(object : TextDiffSettingsHolder.TextDiffSettings.Listener {
      override fun alignModeChanged() {
        viewer.rediff()
      }
    }, this)
  }

  override fun getDiffChanges(): List<AlignableChange> = viewer.diffChanges
}

@ApiStatus.Internal
interface AlignedDiffModel : Disposable {
  fun getDiffChanges(): List<AlignableChange>
  fun needAlignChanges(): Boolean
  fun realignChanges()
  fun clear()
}

@ApiStatus.Internal
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
@ApiStatus.Internal
abstract class AlignedDiffModelBase(
  private val diffRequest: DiffRequest,
  private val diffContext: DiffContext,
  parent: JComponent,
  private val editor1: EditorEx,
  private val editor2: EditorEx,
  private val syncScrollable: SyncScrollSupport.SyncScrollable) : AlignedDiffModel {
  private val queue = MergingUpdateQueue("SimpleAlignedDiffModel", 300, true, parent, this)

  protected val textSettings get() = TextDiffViewerUtil.getTextSettings(diffContext)

  // sorted by highlighter offset
  private val changeInlays = mutableListOf<ChangeInlay>()
  private val mirrorInlays1 = mutableListOf<MirrorInlay>()
  private val mirrorInlays2 = mutableListOf<MirrorInlay>()

  private val rangeHighlighters1 = mutableListOf<RangeHighlighter>()
  private val rangeHighlighters2 = mutableListOf<RangeHighlighter>()

  init {
    val inlayListener = MyInlayModelListener()
    editor1.inlayModel.addListener(inlayListener, this)
    editor2.inlayModel.addListener(inlayListener, this)

    val softWrapListener = MySoftWrapModelListener()
    editor1.softWrapModel.addSoftWrapChangeListener(softWrapListener)
    editor2.softWrapModel.addSoftWrapChangeListener(softWrapListener)
  }

  fun scheduleRealignChanges() {
    if (!needAlignChanges()) return
    queue.queue(Update.create("update") { realignChanges() })
  }

  override fun needAlignChanges(): Boolean {
    val forcedValue: Boolean? = diffRequest.getUserData(DiffUserDataKeys.ALIGNED_TWO_SIDED_DIFF)
    if (forcedValue != null) return forcedValue

    return textSettings.isEnableAligningChangesMode
  }

  override fun realignChanges() {
    if (!needAlignChanges()) return
    if (listOf(editor1, editor2).any { it.isDisposed || (it.foldingModel as FoldingModelImpl).isInBatchFoldingOperation }) return

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

    for (change in getDiffChanges()) {
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
    val editor = getEditor(side)
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
    val highlighter = getEditor(side).markupModel
      .addRangeHighlighter(inlay.offset, inlay.offset, HighlighterLayer.SELECTION, TextAttributes(), HighlighterTargetArea.EXACT_RANGE)
    highlighter.lineMarkerRenderer = DiffInlayGutterMarkerRenderer(diffType, inlay)
    if (side == Side.LEFT) {
      rangeHighlighters1 += highlighter
    }
    else {
      rangeHighlighters2 += highlighter
    }
  }

  private fun getEditor(side: Side): EditorEx {
    return side.selectNotNull(editor1, editor2)
  }

  private fun createMirrorInlay(sourceSide: Side,
                                sourceInlay: Inlay<*>,
                                targetLine: Int): Inlay<AlignDiffInlayRenderer>? {
    val side = sourceSide.other()
    val editor = getEditor(side)
    val offset = DiffUtil.getOffset(editor.document, targetLine, 0)

    val inlayProperties = InlayProperties()
      .showAbove(sourceInlay.properties.isShownAbove)
      .priority(sourceInlay.properties.priority)

    val inlayPresentation = AlignDiffInlayRenderer(editor, null)

    return editor.inlayModel.addBlockElement(offset, inlayProperties, inlayPresentation)
  }

  private fun getMirrorTargetLine(sourceSide: Side, sourceInlay: Inlay<*>): Int {
    val sourceEditor = getEditor(sourceSide)

    val sourceLine = sourceEditor.offsetToLogicalPosition(sourceInlay.offset).line
    return syncScrollable.transfer(sourceSide, sourceLine)
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

      val prefixDelta = calcEffectiveVisualLinesHeight(editor2, last2, range.start2) -
                        calcEffectiveVisualLinesHeight(editor1, last1, range.start1)

      if (prefixDelta >= 0) {
        changeInlay.setTop(prefixDelta, 0)
      }
      else {
        changeInlay.setTop(0, -prefixDelta)
      }

      val bodyDelta =
        calcEffectiveVisualLinesHeight(editor2, range.start2, range.end2) -
        calcEffectiveVisualLinesHeight(editor1, range.start1, range.end1) +
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

  /**
   * Make adjustments to SoftWraps and folded lines
   */
  private fun calcEffectiveVisualLinesHeight(editor: Editor, startLine: Int, endLine: Int): Int {
    if (startLine == endLine) return 0

    val document = editor.document
    val lineCount = document.lineCount
    if (lineCount == 0) return 0

    val vLine1 = editor.offsetToVisualLine(document.getLineStartOffset(startLine), false)
    val vLine2 = if (endLine == lineCount) {
      editor.offsetToVisualLine(document.getLineStartOffset(endLine - 1), false) + 1
    }
    else {
      editor.offsetToVisualLine(document.getLineStartOffset(endLine), false)
    }
    return editor.lineHeight * (vLine2 - vLine1)
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

  private val AlignableChange.range: Range
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
    val change: AlignableChange,

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