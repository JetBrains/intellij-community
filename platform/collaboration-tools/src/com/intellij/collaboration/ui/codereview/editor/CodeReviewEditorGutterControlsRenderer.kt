// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.codeInsight.documentation.render.DocRenderer
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.THREAD_TOP_MARGIN
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import icons.CollaborationToolsIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.Icon
import kotlin.properties.Delegates.observable

/**
 * Draws and handles review controls in gutter
 */
class CodeReviewEditorGutterControlsRenderer
private constructor(
  private val model: CodeReviewEditorGutterControlsModel,
  private val editor: EditorEx,
  initialState: CodeReviewEditorGutterControlsModel.ControlsState,
) : LineMarkerRenderer, LineMarkerRendererEx, ActiveGutterRenderer, Disposable {

  private var hoveredLogicalLine: Int? = null
  private var columnHovered: Boolean = false
  private var selectedRangeForMultilineComment: LineRange? = null

  @set:RequiresEdt
  private var state: CodeReviewEditorGutterControlsModel.ControlsState by observable(initialState) { _, oldState, newState ->
    if (newState != oldState) {
      repaintColumn(editor)
    }
  }

  private val hoverHandler = HoverHandler(editor)
  private val multilineCommentSelectionListener = MultilineCommentSelectionListener()

  init {
    editor.addEditorMouseListener(hoverHandler)
    editor.addEditorMouseMotionListener(hoverHandler)
    editor.selectionModel.addSelectionListener(multilineCommentSelectionListener)

    editor.gutterComponentEx.reserveLeftFreePaintersAreaWidth(this, ICON_AREA_WIDTH)
  }

  override fun dispose() {
    editor.removeEditorMouseListener(hoverHandler)
    editor.removeEditorMouseMotionListener(hoverHandler)
    editor.selectionModel.removeSelectionListener(multilineCommentSelectionListener)
  }

  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    if (editor !is EditorImpl) return
    paintCommentIcons(editor, g, r)
    paintHoveredLineIcons(editor, g, r)
    paintIconForMultiline(editor, g, r)
  }

  /**
   * Paint comment icons on each line containing discussion renderers
   */
  private fun paintCommentIcons(editor: EditorImpl, g: Graphics, r: Rectangle) {
    val hoveredLine = hoverHandler.calcHoveredLineData()?.logicalLine
    val icon = EditorUIUtil.scaleIcon(CollaborationToolsIcons.Comment, editor)
    state.linesWithComments.forEach { lineIdx ->
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

    for ((action, yRange) in layoutActions(lineData, editor.lineHeight)) {
      val rawIcon = if (lineData.columnHovered) action.hoveredIcon else action.icon
      val icon = EditorUIUtil.scaleIcon(rawIcon, editor)

      icon.paintIcon(null, g, r.x, yRange.first)
    }
  }

  /**
   * Paint a new comment icon on the last line of selected range as a setup for multiline comment
   */
  private fun paintIconForMultiline(editor: EditorImpl, g: Graphics, r: Rectangle) {
    val selectionEndLine = selectedRangeForMultilineComment?.end
    if (selectionEndLine != null && selectionEndLine in 0 until editor.document.lineCount) {
      val lineData = LogicalLineData(this.editor, state, selectionEndLine, false)
      for ((action, yRange) in layoutActions(lineData, editor.lineHeight)) {
        val rawIcon = if (lineData.columnHovered) action.hoveredIcon else action.icon
        val icon = EditorUIUtil.scaleIcon(rawIcon, editor)
        icon.paintIcon(null, g, r.x, yRange.first)
      }
    }
  }

  override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
    val lineData = hoverHandler.calcHoveredLineData() ?: return false
    if (!lineData.columnHovered) return false

    val actions = layoutActions(lineData, editor.lineHeight)
    return actions.entries.any { (_, yRange) -> e.y in yRange }
  }

  override fun doAction(editor: Editor, e: MouseEvent) {
    val lineData = hoverHandler.calcHoveredLineData() ?: return
    if (!lineData.columnHovered) return

    val actions = layoutActions(lineData, editor.lineHeight)
    val action = actions.entries.firstOrNull { (_, yRange) -> e.y in yRange }?.key ?: return

    action.doAction()

    e.consume()
  }

  private fun layoutActions(lineData: LogicalLineData, lineHeight: Int): Map<GutterAction, IntRange> {
    val actions = lineData.getActions()
    val yRange = lineData.yRangeWithInlays

    if (actions.isEmpty()) return emptyMap()

    var y = yRange.first
    return actions.associateWith { action ->
      val iconHeight = action.icon.iconHeight
      val iconPadding = (lineHeight - iconHeight) / 2

      if (action.actionType == GutterAction.ActionType.CLOSE_NEW_COMMENT) {
        val visualLine = editor.offsetToVisualLine(editor.document.getLineEndOffset(lineData.logicalLine), true)
        val lastThread = editor.inlayModel.getBlockElementsForVisualLine(visualLine, false).lastOrNull()

        // makes sure that icons don't overlap
        y = maxOf(y, lastThread?.bounds?.y?.minus(iconPadding) ?: y) + JBUI.scale(THREAD_TOP_MARGIN)
      }

      val range = y + iconPadding..y + iconPadding + iconHeight
      if (range.last > yRange.last) return@associateWith null

      y += lineHeight

      range
    }.filterValues { it != null }.mapValues { it.value!! }
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

  private inner class MultilineCommentSelectionListener : SelectionListener {
    override fun selectionChanged(e: SelectionEvent) {
      if (e.newRange.isEmpty) {
        selectedRangeForMultilineComment = null
        return
      }
      val selectedRange = e.newRange.takeIf { it.length > 0 }
        ?.let { LineRange(editor.offsetToLogicalPosition(it.startOffset).line, editor.offsetToLogicalPosition(it.endOffset).line) }
      selectedRangeForMultilineComment = selectedRange
    }
  }

  private fun LogicalLineData.getActions(): List<GutterAction> =
    listOfNotNull(
      if (hasComments) toggleCommentAction else null,
      if (commentable && !hasCommentsUnderFoldedRegion) {
        if (hasNewComment) closeNewCommentAction
        else if (selectedRangeForMultilineComment == null) startNewCommentAction
        // lines in the selected range are not commentable except the last one
        else if (logicalLine !in selectedRangeForMultilineComment!!.let { it.start..it.end }) startNewCommentAction
        else if (logicalLine == selectedRangeForMultilineComment!!.end) startNewCommentAction
        else null
      }
      else null
    )

  private val LogicalLineData.toggleCommentAction
    get() = GutterAction(CollaborationToolsIcons.Comment, GutterAction.ActionType.TOGGLE_COMMENT) { unfoldOrToggle(this) }
  private val LogicalLineData.closeNewCommentAction
    get() = GutterAction(AllIcons.Diff.Remove, GutterAction.ActionType.CLOSE_NEW_COMMENT) {
      model.cancelNewComment(logicalLine)
      repaintColumn(editor)
    }
  private val LogicalLineData.startNewCommentAction
    get() = GutterAction(AllIcons.General.InlineAdd, GutterAction.ActionType.START_NEW_COMMENT, AllIcons.General.InlineAddHover) {
      requestNewComment(logicalLine)
    }

  private fun requestNewComment(logicalLine: Int) {
    if (editor.caretModel.logicalPosition.line != logicalLine)
      editor.caretModel.moveToOffset(editor.document.getLineEndOffset(logicalLine))
    if (model is CodeReviewCommentableEditorModel.WithMultilineComments) {
      val selectedRange = selectedRangeForMultilineComment
      if (selectedRange != null && logicalLine == selectedRange.end && model.canCreateComment(selectedRange)) {
        val scrollingModel = editor.scrollingModel
        scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        scrollingModel.runActionOnScrollingFinished {
          model.requestNewComment(selectedRange)
        }
        return
      }
    }
    model.requestNewComment(logicalLine)
  }

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
      val actionType: ActionType,
      val hoveredIcon: Icon = icon,
      val doAction: () -> Unit,
    ) {
      enum class ActionType {
        TOGGLE_COMMENT,
        CLOSE_NEW_COMMENT,
        START_NEW_COMMENT
      }
    }

    suspend fun render(model: CodeReviewEditorGutterControlsModel, editor: EditorEx): Nothing {
      var renderer: InstalledRenderer? = null

      withContext(Dispatchers.EDT) {
        try {
          model.gutterControlsState.collect { state ->
            if (state != null) {
              if (renderer == null) {
                renderer = InstalledRenderer(model, editor, state)
              }
              else {
                renderer.state = state
              }
            }
            else {
              renderer?.let {
                Disposer.dispose(it)
              }
            }
          }
        }
        finally {
          val renderer = renderer
          if (renderer != null) {
            withContext(NonCancellable + ModalityState.any().asContextElement()) {
              Disposer.dispose(renderer)
            }
          }
        }
      }
    }

    private class InstalledRenderer(
      model: CodeReviewEditorGutterControlsModel,
      editor: EditorEx,
      initialState: CodeReviewEditorGutterControlsModel.ControlsState,
    ) : Disposable {
      private val renderer = CodeReviewEditorGutterControlsRenderer(model, editor, initialState)
      private val highlighter = editor.markupModel.addRangeHighlighter(null, 0, editor.document.textLength,
                                                                       DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                       HighlighterTargetArea.LINES_IN_RANGE).apply {
        setGreedyToLeft(true)
        setGreedyToRight(true)
        setLineMarkerRenderer(renderer)
      }

      var state: CodeReviewEditorGutterControlsModel.ControlsState by renderer::state

      init {
        Disposer.register(this, renderer)
      }

      override fun dispose() {
        highlighter.dispose()
      }
    }
  }
}