// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.fragmented

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorGutter
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.editor.FoldingModel
import com.intellij.openapi.editor.IndentsModel
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.EditorProjection
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ScrollingModel
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EmptyImmutableMarkupModel
import com.intellij.openapi.editor.impl.EmptyIndentsModel
import com.intellij.openapi.editor.impl.ImaginaryCaret
import com.intellij.openapi.editor.impl.ImaginaryCaretModel
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.textarea.EmptyInlayModel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import javax.swing.JComponent
import javax.swing.border.Border

/**
 * The editor of one side of a [UnifiedDiffViewer].
 *
 * The unified editor shows the lines of both sides in one document, that cannot be parsed by normal language parsers.
 *
 * This editor is over the side document, so PSI, code insight and the daemon see the real file.
 * The caret, the selection, the scrolling and the geometry map to the unified editor line by line.
 *
 * The Document highlighting is then copied into the host editor by [com.intellij.diff.tools.fragmented.UnifiedDiffViewer.MarkupUpdater].
 *
 * The [UnifiedSideEditor] is being passed to the code navigation actions, and passes the navigation requests to the host.
 * So it requires much deeper imitation of a proper [com.intellij.openapi.editor.impl.EditorImpl] than the [ImaginaryEditor] does.
 *
 * The [UnifiedSideEditor] shares lifetime with its host editor.
 */
@ApiStatus.Internal
class UnifiedSideEditor(
  private val viewer: UnifiedDiffViewer,
  project: Project,
  val side: Side,
) : ImaginaryEditor(project, viewer.getDocument(side)), EditorProjection {
  private val sideCaretModel = SideCaretModel(this)
  private val sideScrollingModel = SideScrollingModel(this)
  private val sideFoldingModel = SideFoldingModel(this)

  private val host: EditorEx
    get() = viewer.editor

  override val hostEditor: Editor
    get() = viewer.editor

  override fun toHostOffset(offset: Int): Int = viewer.transferOffsetToOnesideStrict(side, offset)

  override fun fromHostOffset(hostOffset: Int): Int = viewer.transferOffsetFromOnesideStrict(side, hostOffset)

  override fun toHostRange(range: TextRange): TextRange? = viewer.transferRangeToOnesideStrict(side, range)

  /** The host offset for a caret move or a scroll: the nearest shown line when the exact line is not known. */
  private fun toHostOffsetApproximate(offset: Int): Int = viewer.transferOffsetToOneside(side, offset).coerceAtLeast(0)

  private fun fromHostOffsetApproximate(hostOffset: Int): Int = viewer.transferOffsetFromOneside(side, hostOffset).coerceAtLeast(0)

  private fun toHostPosition(pos: LogicalPosition): LogicalPosition =
    host.offsetToLogicalPosition(toHostOffsetApproximate(logicalPositionToOffset(pos)))

  private fun fromHostPosition(pos: LogicalPosition): LogicalPosition =
    offsetToLogicalPosition(fromHostOffsetApproximate(host.logicalPositionToOffset(pos)))

  override fun getCaretModel(): CaretModel = sideCaretModel

  override fun getScrollingModel(): ScrollingModel = sideScrollingModel

  override fun getFoldingModel(): FoldingModel = sideFoldingModel

  override fun getMarkupModel(): MarkupModel = EmptyImmutableMarkupModel(document)

  override fun getIndentsModel(): IndentsModel = EmptyIndentsModel()

  override fun getInlayModel(): InlayModel = EmptyInlayModel()

  override fun getVirtualFile(): VirtualFile? = FileDocumentManager.getInstance().getFile(document)

  override fun getEditorKind(): EditorKind = EditorKind.DIFF

  override fun isViewer(): Boolean = true

  override fun isOneLineMode(): Boolean = false

  override fun isDisposed(): Boolean = viewer.isDisposed || host.isDisposed

  override fun getColorsScheme(): EditorColorsScheme = host.colorsScheme

  override fun getSettings(): EditorSettings = host.settings

  override fun getComponent(): JComponent = host.component

  override fun getContentComponent(): JComponent = host.contentComponent

  /** The unified editor owns its border. */
  override fun setBorder(border: Border?): Unit = Unit

  override fun getInsets(): Insets = host.insets

  override fun getGutter(): EditorGutter = host.gutter

  override fun getMouseEventArea(e: MouseEvent): EditorMouseEventArea? = host.getMouseEventArea(e)

  override fun hasHeaderComponent(): Boolean = false

  override fun getHeaderComponent(): JComponent? = null

  /** The unified editor owns its header. */
  override fun setHeaderComponent(header: JComponent?): Unit = Unit

  override fun getLineHeight(): Int = host.lineHeight

  override fun addEditorMouseListener(listener: EditorMouseListener): Unit = host.addEditorMouseListener(listener)

  override fun removeEditorMouseListener(listener: EditorMouseListener): Unit = host.removeEditorMouseListener(listener)

  override fun addEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = host.addEditorMouseMotionListener(listener)

  override fun removeEditorMouseMotionListener(listener: EditorMouseMotionListener): Unit = host.removeEditorMouseMotionListener(listener)

  override fun logicalPositionToXY(pos: LogicalPosition): Point = host.logicalPositionToXY(toHostPosition(pos))

  override fun logicalToVisualPosition(logicalPos: LogicalPosition): VisualPosition = host.logicalToVisualPosition(toHostPosition(logicalPos))

  override fun visualToLogicalPosition(visiblePos: VisualPosition): LogicalPosition = fromHostPosition(host.visualToLogicalPosition(visiblePos))

  override fun visualPositionToXY(visible: VisualPosition): Point = host.visualPositionToXY(visible)

  override fun visualPositionToPoint2D(pos: VisualPosition): Point2D = host.visualPositionToPoint2D(pos)

  override fun xyToLogicalPosition(p: Point): LogicalPosition = fromHostPosition(host.xyToLogicalPosition(p))

  override fun xyToVisualPosition(p: Point): VisualPosition = host.xyToVisualPosition(p)

  override fun xyToVisualPosition(p: Point2D): VisualPosition = host.xyToVisualPosition(p)

  override fun calculateVisibleRange(): ProperTextRange {
    if (viewer.diffChanges == null) return ProperTextRange(0, 0)

    val hostDocument = host.document
    val hostRange = host.calculateVisibleRange()
    val hostLine1 = hostDocument.getLineNumber(hostRange.startOffset)
    val hostLine2 = hostDocument.getLineNumber(hostRange.endOffset)

    val sideLine1 = viewer.transferLineFromOneside(side, maxOf(0, hostLine1 - 1))
    val sideLine2 = viewer.transferLineFromOneside(side, minOf(DiffUtil.getLineCount(hostDocument), hostLine2 + 1))
    if (sideLine1 < 0 || sideLine1 > sideLine2 || sideLine2 > DiffUtil.getLineCount(document)) return ProperTextRange(0, 0)

    val sideRange = DiffUtil.getLinesRange(document, sideLine1, sideLine2, false)
    return ProperTextRange.create(sideRange.startOffset, sideRange.endOffset)
  }

  override fun toString(): String = "UnifiedSideEditor($side, $document)"

  /** One caret, which is a view of the primary caret of the host editor. */
  private class SideCaretModel(val sideEditor: UnifiedSideEditor) : ImaginaryCaretModel(sideEditor) {
    private val caret = SideCaret(sideEditor, this)

    override fun getPrimaryCaret(): Caret = caret

    override fun getTextAttributes(): TextAttributes? = sideEditor.host.caretModel.textAttributes

    override fun runBatchCaretOperation(runnable: Runnable): Unit = sideEditor.host.caretModel.runBatchCaretOperation(runnable)
  }

  private class SideCaret(val sideEditor: UnifiedSideEditor, caretModel: SideCaretModel) : ImaginaryCaret(caretModel) {
    private val hostCaret: Caret
      get() = sideEditor.host.caretModel.primaryCaret

    override fun getOffset(): Int = sideEditor.fromHostOffsetApproximate(hostCaret.offset)

    override fun moveToOffset(offset: Int, locateBeforeSoftWrap: Boolean) {
      hostCaret.moveToOffset(sideEditor.toHostOffsetApproximate(offset.coerceIn(0, sideEditor.document.textLength)), locateBeforeSoftWrap)
    }

    override fun getLogicalPosition(): LogicalPosition = sideEditor.offsetToLogicalPosition(getOffset())

    override fun getVisualPosition(): VisualPosition = hostCaret.visualPosition

    override fun isUpToDate(): Boolean = hostCaret.isUpToDate

    override fun isValid(): Boolean = !sideEditor.isDisposed && hostCaret.isValid

    override fun getSelectionStart(): Int = sideEditor.fromHostOffsetApproximate(hostCaret.selectionStart)

    override fun getSelectionEnd(): Int = sideEditor.fromHostOffsetApproximate(hostCaret.selectionEnd)

    override fun hasSelection(): Boolean = hostCaret.hasSelection() && selectionStart < selectionEnd

    override fun getSelectionStartPosition(): VisualPosition = hostCaret.selectionStartPosition

    override fun getSelectionEndPosition(): VisualPosition = hostCaret.selectionEndPosition

    override fun getSelectedText(): String = sideEditor.document.getText(TextRange(selectionStart, selectionEnd))

    override fun getLeadSelectionOffset(): Int = sideEditor.fromHostOffsetApproximate(hostCaret.leadSelectionOffset)

    override fun setSelection(startOffset: Int, endOffset: Int): Unit = setSelection(startOffset, endOffset, true)

    override fun setSelection(startOffset: Int, endOffset: Int, updateSystemSelection: Boolean) {
      hostCaret.setSelection(sideEditor.toHostOffsetApproximate(startOffset),
                             sideEditor.toHostOffsetApproximate(endOffset),
                             updateSystemSelection)
    }

    override fun removeSelection(): Unit = hostCaret.removeSelection()

    override fun moveCaretRelatively(columnShift: Int, lineShift: Int, withSelection: Boolean, scrollToCaret: Boolean) {
      val anchor = if (withSelection && hasSelection()) leadSelectionOffset else getOffset()
      val target = if (lineShift == 0) {
        getOffset() + columnShift
      }
      else {
        val position = logicalPosition
        sideEditor.logicalPositionToOffset(LogicalPosition(position.line + lineShift, position.column + columnShift))
      }
      moveToOffset(target)
      if (withSelection) setSelection(minOf(anchor, getOffset()), maxOf(anchor, getOffset()))
      if (scrollToCaret) sideEditor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }
  }

  /**
   * A view of the fold regions of the host editor in the offsets of this side.
   * It expands and collapses a region, and it adds and removes none, so a folding pass changes nothing through it.
   */
  private class SideFoldingModel(val sideEditor: UnifiedSideEditor) : FoldingModel, ModificationTracker {
    private val hostModel: FoldingModel
      get() = sideEditor.host.foldingModel

    override fun getAllFoldRegions(): Array<FoldRegion> = hostModel.allFoldRegions.mapNotNull { wrap(it) }.toTypedArray()

    override fun isOffsetCollapsed(offset: Int): Boolean = hostModel.isOffsetCollapsed(sideEditor.toHostOffsetApproximate(offset))

    override fun getCollapsedRegionAtOffset(offset: Int): FoldRegion? =
      hostModel.getCollapsedRegionAtOffset(sideEditor.toHostOffsetApproximate(offset))?.let { wrap(it) }

    override fun getFoldRegion(startOffset: Int, endOffset: Int): FoldRegion? {
      val hostRange = sideEditor.toHostRange(TextRange(startOffset, endOffset)) ?: return null
      return hostModel.getFoldRegion(hostRange.startOffset, hostRange.endOffset)?.let { wrap(it) }
    }

    override fun addFoldRegion(startOffset: Int, endOffset: Int, placeholderText: String): FoldRegion? = null

    override fun removeFoldRegion(region: FoldRegion): Unit = Unit

    override fun runBatchFoldingOperation(operation: Runnable, allowMovingCaret: Boolean, keepRelativeCaretPosition: Boolean): Unit =
      hostModel.runBatchFoldingOperation(operation, allowMovingCaret, keepRelativeCaretPosition)

    override fun getModificationCount(): Long = (hostModel as? ModificationTracker)?.modificationCount ?: 0

    private fun wrap(hostRegion: FoldRegion): SideFoldRegion? {
      val lastOffset = maxOf(hostRegion.startOffset, hostRegion.endOffset - 1)
      if (sideEditor.fromHostOffset(hostRegion.startOffset) == -1 || sideEditor.fromHostOffset(lastOffset) == -1) return null
      return SideFoldRegion(sideEditor, hostRegion)
    }
  }

  /**
   * A view of a fold region of the host editor in the offsets of the side. Two views of one host region are equal.
   */
  private class SideFoldRegion(
    private val sideEditor: UnifiedSideEditor,
    private val hostRegion: FoldRegion,
  ) : FoldRegion {
    override fun equals(other: Any?): Boolean = other is SideFoldRegion && other.sideEditor === sideEditor && other.hostRegion == hostRegion

    override fun hashCode(): Int = hostRegion.hashCode()

    override fun getDocument(): Document = sideEditor.document

    override fun getStartOffset(): Int = sideEditor.fromHostOffsetApproximate(hostRegion.startOffset)

    override fun getEndOffset(): Int = sideEditor.fromHostOffsetApproximate(hostRegion.endOffset)

    override fun getTextRange(): TextRange = TextRange(startOffset, endOffset)

    override fun contains(offset: Int): Boolean = offset >= startOffset && offset < endOffset

    override fun containsInclusive(offset: Int): Boolean = offset >= startOffset && offset <= endOffset

    override fun getEditor(): Editor = sideEditor

    override fun isValid(): Boolean = hostRegion.isValid

    override fun isExpanded(): Boolean = hostRegion.isExpanded

    override fun setExpanded(expanded: Boolean): Unit = hostRegion.setExpanded(expanded)

    override fun getPlaceholderText(): String = hostRegion.placeholderText

    override fun setPlaceholderText(text: String): Unit = hostRegion.setPlaceholderText(text)

    override fun getGroup(): FoldingGroup? = hostRegion.group

    override fun shouldNeverExpand(): Boolean = hostRegion.shouldNeverExpand()

    override fun setInnerHighlightersMuted(value: Boolean): Unit = hostRegion.setInnerHighlightersMuted(value)

    override fun areInnerHighlightersMuted(): Boolean = hostRegion.areInnerHighlightersMuted()

    override fun setGutterMarkEnabledForSingleLine(value: Boolean): Unit = hostRegion.setGutterMarkEnabledForSingleLine(value)

    override fun isGutterMarkEnabledForSingleLine(): Boolean = hostRegion.isGutterMarkEnabledForSingleLine

    override fun isGreedyToLeft(): Boolean = hostRegion.isGreedyToLeft

    override fun isGreedyToRight(): Boolean = hostRegion.isGreedyToRight

    override fun setGreedyToLeft(greedy: Boolean): Unit = hostRegion.setGreedyToLeft(greedy)

    override fun setGreedyToRight(greedy: Boolean): Unit = hostRegion.setGreedyToRight(greedy)

    override fun setStickingToRight(value: Boolean): Unit = hostRegion.setStickingToRight(value)

    override fun <T> getUserData(key: Key<T>): T? = hostRegion.getUserData(key)

    override fun <T> putUserData(key: Key<T>, value: T?): Unit = hostRegion.putUserData(key, value)

    /**
     * The side editor does not remove the folding region of the host editor.
     *
     * It is also not a proper [com.intellij.openapi.util.Disposer] root: `Disposer.register(SideFoldRegion, child)` will leak.
     */
    override fun dispose(): Unit = Unit

    override fun toString(): String = "UnifiedSideEditor.FoldRegion(${sideEditor.side}, $hostRegion)"
  }

  /** The scrolling model of the host editor, with a position mapped to it. */
  private class SideScrollingModel(private val sideEditor: UnifiedSideEditor) : ScrollingModel {
    private val hostModel: ScrollingModel
      get() = sideEditor.hostEditor.scrollingModel

    override fun getVisibleArea(): Rectangle = hostModel.visibleArea

    override fun getVisibleAreaOnScrollingFinished(): Rectangle = hostModel.visibleAreaOnScrollingFinished

    override fun scrollToCaret(scrollType: ScrollType): Unit = hostModel.scrollToCaret(scrollType)

    override fun scrollTo(pos: LogicalPosition, scrollType: ScrollType): Unit = hostModel.scrollTo(sideEditor.toHostPosition(pos), scrollType)

    override fun runActionOnScrollingFinished(action: Runnable): Unit = hostModel.runActionOnScrollingFinished(action)

    override fun disableAnimation(): Unit = hostModel.disableAnimation()

    override fun enableAnimation(): Unit = hostModel.enableAnimation()

    override fun getVerticalScrollOffset(): Int = hostModel.verticalScrollOffset

    override fun getHorizontalScrollOffset(): Int = hostModel.horizontalScrollOffset

    override fun scrollVertically(scrollOffset: Int): Unit = hostModel.scrollVertically(scrollOffset)

    override fun scrollHorizontally(scrollOffset: Int): Unit = hostModel.scrollHorizontally(scrollOffset)

    override fun scroll(horizontalOffset: Int, verticalOffset: Int): Unit = hostModel.scroll(horizontalOffset, verticalOffset)

    override fun addVisibleAreaListener(listener: VisibleAreaListener): Unit = hostModel.addVisibleAreaListener(listener)

    override fun removeVisibleAreaListener(listener: VisibleAreaListener): Unit = hostModel.removeVisibleAreaListener(listener)
  }
}
