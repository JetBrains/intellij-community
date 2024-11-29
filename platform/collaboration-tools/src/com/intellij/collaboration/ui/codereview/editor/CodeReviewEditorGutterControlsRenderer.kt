// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.collaboration.async.launchNow
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.math.min
import kotlin.properties.Delegates.observable

/**
 * Draws and handles review controls in gutter
 */
class CodeReviewEditorGutterControlsRenderer
private constructor(
  private val model: CodeReviewEditorGutterControlsModel,
  private val editor: EditorEx,
)
  : LineMarkerRenderer, LineMarkerRendererEx, ActiveGutterRenderer {

  private var hoveredLogicalLine: Int? = null
  private var columnHovered: Boolean = false

  private var state: CodeReviewEditorGutterControlsModel.ControlsState? by observable(null) { _, _, newState ->
    hoveredLogicalLine = null
    columnHovered = false
    if (newState != null) {
      repaintColumn(editor)
    }
  }

  private val hoverHandler = HoverHandler(editor)

  suspend fun launch(): Nothing {
    withContext(Dispatchers.Main) {
      val areaDisposable = Disposer.newDisposable()
      editor.gutterComponentEx.reserveLeftFreePaintersAreaWidth(areaDisposable, ICON_AREA_WIDTH)
      editor.addEditorMouseListener(hoverHandler)
      editor.addEditorMouseMotionListener(hoverHandler)

      try {
        model.gutterControlsState.collect {
          state = it
        }
      }
      finally {
        withContext(NonCancellable) {
          editor.removeEditorMouseListener(hoverHandler)
          editor.removeEditorMouseMotionListener(hoverHandler)
          Disposer.dispose(areaDisposable)
        }
      }
    }
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    if (editor !is EditorImpl) return
    paintCommentIcons(editor, g, r)
    paintHoveredLineIcons(editor, g, r)
  }

  /**
   * Paint comment icons on each line containing discussion renderers
   */
  private fun paintCommentIcons(editor: EditorImpl, g: Graphics, r: Rectangle) {
    val hoveredLine = hoverHandler.calcHoveredLineData()?.logicalLine
    val icon = EditorUIUtil.scaleIcon(CollaborationToolsIcons.Comment, editor)
    (state ?: return).linesWithComments.forEach { lineIdx ->
      if (lineIdx in 0 until editor.document.lineCount && lineIdx != hoveredLine) {
        val yRange = EditorUtil.logicalLineToYRange(editor, lineIdx).first
        val lineCenter = yRange.intervalStart() + editor.lineHeight / 2
        val y = lineCenter - icon.iconWidth / 2
        icon.paintIcon(null, g, r.x, y)
      }
    }
  }

  /**
   * Paint a new comment icon on hovered line if line is not folded and if there's enough vertical space
   */
  private fun paintHoveredLineIcons(editor: EditorImpl, g: Graphics, r: Rectangle) {
    val lineData = hoverHandler.calcHoveredLineData() ?: return

    var yShift = 0
    for (action in lineData.getActions()) {
      val rawIcon = if (lineData.columnHovered) action.hoveredIcon else action.icon
      val icon = EditorUIUtil.scaleIcon(rawIcon, editor)

      val y = lineData.yRangeWithInlays.first + yShift + (editor.lineHeight - icon.iconHeight) / 2
      icon.paintIcon(null, g, r.x, y)

      yShift += editor.lineHeight
      // do not paint if there's not enough space
      if (yShift > 0 && lineData.yRangeWithInlays.last - lineData.yRangeWithInlays.first < yShift + editor.lineHeight) break
    }

  }

  override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val lineData = hoverHandler.calcHoveredLineData() ?: return false
    if (!lineData.columnHovered) return false

    val actions = lineData.getActions()
    if (actions.isEmpty()) return false

    val actionableHeight = editor.lineHeight * actions.size
    val yRange = lineData.yRangeWithInlays
    val actionableYStart = yRange.first
    val actionableYEnd = min(yRange.first + actionableHeight, yRange.last)

    return e.y in actionableYStart..actionableYEnd
  }

  override fun doAction(editor: Editor, e: MouseEvent) {
    val lineData = hoverHandler.calcHoveredLineData() ?: return
    if (!lineData.columnHovered) return

    val hoveredIconIdx = getHoveredIconSlotIndex(lineData.yRangeWithInlays, e.y)
    val action = lineData.getActions().getOrNull(hoveredIconIdx) ?: return

    action.doAction()

    e.consume()
  }

  private fun getHoveredIconSlotIndex(range: IntRange, y: Int): Int {
    if (y < range.first) return -1
    var idx: Int = -1
    for (slotEnd in range.first + editor.lineHeight..range.last step editor.lineHeight) {
      idx++
      if (y < slotEnd) {
        break
      }
    }
    return idx
  }

  private fun unfoldOrToggle(lineData: LogicalLineData) {
    val foldedRegion = lineData.foldedRegion
    if (foldedRegion != null) foldedRegion.unfold() else model.toggleComments(lineData.logicalLine)
  }

  private fun FoldRegion.unfold() {
    if (this is CustomFoldRegion) {
      val renderer = renderer
      if (renderer is DocRenderer) {
        renderer.item.toggle()
        return
      }
    }
    else {
      editor.foldingModel.runBatchFoldingOperation {
        isExpanded = true
      }
    }
  }

  override fun getPosition(): LineMarkerRendererEx.Position = LineMarkerRendererEx.Position.LEFT

  /**
   * Handles the hover state of the rendered icons
   * Use [calcHoveredLineData] to acquire a current state
   */
  private inner class HoverHandler(private val editor: EditorEx) : EditorMouseListener, EditorMouseMotionListener {
    fun calcHoveredLineData(): LogicalLineData? {
      val logicalLine = hoveredLogicalLine?.takeIf { it in 0 until editor.document.lineCount } ?: return null
      val state = state ?: return null
      return LogicalLineData(editor, state, logicalLine, columnHovered)
    }

    override fun mouseMoved(e: EditorMouseEvent) {
      val line = e.logicalPosition.line.coerceAtLeast(0)
      val prevLine = if (line != hoveredLogicalLine) hoveredLogicalLine else null
      if (line in 0 until DiffUtil.getLineCount(editor.document)) {
        hoveredLogicalLine = line
      }
      else {
        hoveredLogicalLine = null
      }
      columnHovered = isIconColumnHovered(editor, e.mouseEvent)
      if (prevLine != null) {
        repaintColumn(editor, prevLine)
      }
      repaintColumn(editor, e.logicalPosition.line)
    }

    override fun mouseExited(e: EditorMouseEvent) {
      repaintColumn(editor, hoveredLogicalLine)
      hoveredLogicalLine = null
      columnHovered = false
    }
  }

  private fun LogicalLineData.getActions(): List<GutterAction> =
    listOfNotNull(
      if (hasComments) toggleCommentAction else null,
      if (commentable && !hasCommentsUnderFoldedRegion) {
        if (hasNewComment) closeNewCommentAction else startNewCommentAction
      }
      else null
    )

  private val LogicalLineData.toggleCommentAction
    get() = GutterAction(CollaborationToolsIcons.Comment) { unfoldOrToggle(this) }
  private val LogicalLineData.closeNewCommentAction
    get() = GutterAction(AllIcons.General.InlineClose, AllIcons.General.InlineCloseHover) { model.cancelNewComment(logicalLine) }
  private val LogicalLineData.startNewCommentAction
    get() = GutterAction(AllIcons.General.InlineAdd, AllIcons.General.InlineAddHover) { model.requestNewComment(logicalLine) }

  companion object {
    private const val ICON_AREA_WIDTH = 16

    private fun repaintColumn(editor: EditorEx, line: Int? = null) {
      val xRange = getIconColumnXRange(editor)
      val yStart: Int
      val yHeight: Int
      if (line != null && line > 0) {
        yStart = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
        yHeight = editor.lineHeight * 2
      }
      else {
        yStart = 0
        yHeight = editor.gutterComponentEx.height
      }
      editor.gutterComponentEx.repaint(xRange.first, yStart, xRange.last - xRange.first, yHeight)
    }

    private fun getIconColumnXRange(editor: EditorEx): IntRange {
      val gutter = editor.gutterComponentEx
      val uiScaledIconAreaWidth = JBUIScale.scale(ICON_AREA_WIDTH)
      val iconAreaWidth =
        // Same calculation as within com.intellij.openapi.editor.impl.EditorGutterComponentImpl#getLeftFreePaintersAreaWidth
        if (editor is EditorImpl && ExperimentalUI.isNewUI()) EditorUIUtil.scaleWidth(uiScaledIconAreaWidth, editor) + 2
        else uiScaledIconAreaWidth
      val iconStart = if (editor.verticalScrollbarOrientation == EditorEx.VERTICAL_SCROLLBAR_RIGHT) {
        gutter.lineMarkerAreaOffset
      }
      else {
        gutter.width - gutter.lineMarkerAreaOffset - iconAreaWidth
      }
      // Correct for inclusive range with -1
      val iconEnd = iconStart + iconAreaWidth - 1
      return iconStart..iconEnd
    }

    private fun isIconColumnHovered(editor: EditorEx, e: MouseEvent): Boolean {
      if (e.component !== editor.gutter) return false
      return e.x in getIconColumnXRange(editor)
    }

    private class LogicalLineData(
      editor: EditorEx, state: CodeReviewEditorGutterControlsModel.ControlsState, val logicalLine: Int, val columnHovered: Boolean,
    ) {
      private val lineStartOffset = editor.document.getLineStartOffset(logicalLine)
      private val lineEndOffset = editor.document.getLineEndOffset(logicalLine)

      private val yRange by lazy {
        val startVisualLine = editor.offsetToVisualLine(lineStartOffset, false)
        val softWrapCount = editor.getSoftWrapModel().getSoftWrapsForRange(lineStartOffset + 1, lineEndOffset - 1).size
        val endVisualLine = startVisualLine + softWrapCount
        val startY = editor.visualLineToY(startVisualLine)
        val endY = (if (endVisualLine == startVisualLine) startY else editor.visualLineToY(endVisualLine)) + editor.getLineHeight()
        startY..endY
      }

      val foldedRegion: FoldRegion? by lazy {
        editor.foldingModel.getCollapsedRegionAtOffset(lineEndOffset)
      }

      val yRangeWithInlays: IntRange by lazy {
        val visualLine = editor.offsetToVisualLine(lineEndOffset, false)
        var inlaysBelowHeight = 0
        editor.inlayModel.getBlockElementsForVisualLine(visualLine, false).forEach {
          inlaysBelowHeight += it.heightInPixels
        }
        yRange.first..yRange.last + inlaysBelowHeight.coerceAtLeast(0)
      }

      val hasComments: Boolean by lazy {
        state.linesWithComments.contains(logicalLine)
      }

      val hasCommentsUnderFoldedRegion: Boolean by lazy {
        val region = foldedRegion ?: return@lazy false

        val (frStart, frEnd) = with(editor.document) {
          getLineNumber(region.startOffset) to getLineNumber(region.endOffset)
        }

        (frStart..frEnd).any { line -> line in state.linesWithComments }
      }

      val hasNewComment: Boolean by lazy {
        state.linesWithNewComments.contains(logicalLine)
      }

      val commentable: Boolean by lazy {
        state.isLineCommentable(logicalLine)
      }
    }

    private data class GutterAction(
      val icon: Icon,
      val hoveredIcon: Icon = icon,
      val doAction: () -> Unit,
    )

    @Deprecated("Use a suspending function", ReplaceWith("cs.launch { render(model, editor) }"))
    fun setupIn(cs: CoroutineScope, model: CodeReviewEditorGutterControlsModel, editor: EditorEx) {
      cs.launchNow(Dispatchers.Main) {
        render(model, editor)
      }
    }

    suspend fun render(model: CodeReviewEditorGutterControlsModel, editor: EditorEx): Nothing {
      withContext(Dispatchers.Main) {
        val renderer = CodeReviewEditorGutterControlsRenderer(model, editor)
        val highlighter = editor.markupModel.addRangeHighlighter(null, 0, editor.document.textLength,
                                                                 DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                 HighlighterTargetArea.LINES_IN_RANGE).apply {
          setGreedyToLeft(true)
          setGreedyToRight(true)
          setLineMarkerRenderer(renderer)
        }
        try {
          renderer.launch()
        }
        finally {
          withContext(NonCancellable + ModalityState.any().asContextElement()) {
            highlighter.dispose()
          }
        }
      }
    }
  }
}