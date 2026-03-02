package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.comment.CommentedCodeFrameRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel.Ranged.Adjustable.AdjustmentDisabledReason
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.diff.util.Side
import com.intellij.ide.IdeTooltip
import com.intellij.ide.IdeTooltipManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiImmediate
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.observable.util.addMouseHoverListener
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.hover.HoverStateListener
import com.intellij.util.asSafely
import com.intellij.util.ui.FocusUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayer
import javax.swing.plaf.LayerUI

private const val OUTLINE_OUTSIDE_DETECTION_MARGIN = 3
private const val OUTLINE_DETECTION_LINE_FRACTION = 0.3f

@ApiStatus.Experimental
object CodeReviewEditorInlayRangeOutlineUtils {
  suspend fun showInlayOutline(
    editor: EditorEx,
    editorModel: CodeReviewCommentableEditorModel.WithMultilineComments,
    inlayModel: CodeReviewInlayModel.Ranged,
    inlayRenderer: CodeReviewComponentInlayRenderer,
    activeRangesTracker: CodeReviewActiveRangesTracker,
  ): Nothing =
    withContext(CoroutineName("Comment inlay hover controller") + Dispatchers.UiImmediate) {
      if (inlayModel is CodeReviewInlayModel.Ranged.Adjustable) {
        inlayModel.range.collectLatest { range ->
          if (range == null) return@collectLatest
          editor.showAdjustableOutline(editorModel, inlayModel, inlayRenderer, activeRangesTracker, range)
        }
      }
      else {
        val isFocusedOrHovered = inlayRenderer.component.isFocusedOrHovered()
        inlayModel.range.combine(isFocusedOrHovered) { range, shouldShowOutline ->
          range.takeIf { shouldShowOutline }
        }.collectLatest { range ->
          if (range != null) {
            editor.showOutline(activeRangesTracker, range)
          }
        }
      }
      awaitCancellation()
    }

  fun wrapWithDimming(component: JComponent, inlayModel: CodeReviewInlayModel.Ranged, activeRangesTracker: CodeReviewActiveRangesTracker): JComponent {
    val fadeLayerUI = FadeLayerUI()
    return JLayer(component, fadeLayerUI).also {
      it.launchOnShow("Inlay.Dimming", Dispatchers.UI) {
        activeRangesTracker.activeRanges.combine(inlayModel.range, ::Pair).collect { (rangesToDim, commentRange) ->
          val shouldDim = rangesToDim
            .filter { commentRange?.end != it.end }
            .any { range -> commentRange?.let { range.contains(it.end, it.end) } == true }
          fadeLayerUI.setAlpha(if (shouldDim) 0.5f else 1f)
          it.repaint()
        }
      }
    }
  }
}

private suspend fun EditorEx.showOutline(activeRangesTracker: CodeReviewActiveRangesTracker, lineRange: LineRange): Nothing {
  // It would be more correct to add the highlighter between the start offset of the startLine and the end offset of the endLine.
  // However, in this situation the highlighter would be invalidated when the line is deleted,
  // and if the comment is placed on the first line, the highlighter would not be recreated,
  // because [inlayModel.range] doesn't change in this case.
  val startOffset = document.getLineStartOffset(lineRange.start)
  val endOffset = document.getLineStartOffset(lineRange.end)

  val editorSide = if (verticalScrollbarOrientation == EditorEx.VERTICAL_SCROLLBAR_LEFT) Side.LEFT else Side.RIGHT
  val renderer = CommentedCodeFrameRenderer(lineRange.start, lineRange.end, editorSide)
  val activeLineHighlighter = markupModel.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE).also { highlighter ->
    highlighter.customRenderer = renderer
    highlighter.lineMarkerRenderer = renderer
  }
  try {
    activeRangesTracker.rangeActivated(lineRange)
  }
  finally {
    markupModel.removeHighlighter(activeLineHighlighter)
  }
}

private suspend fun EditorEx.showAdjustableOutline(
  editorModel: CodeReviewCommentableEditorModel.WithMultilineComments,
  inlayModel: CodeReviewInlayModel.Ranged.Adjustable,
  inlayRenderer: CodeReviewComponentInlayRenderer,
  activeRangesTracker: CodeReviewActiveRangesTracker,
  range: LineRange,
) {
  ResizableOutlineHandler.showResizableOutline(
    this,
    inlayModel,
    inlayRenderer,
    activeRangesTracker,
    range,
    editorModel::canCreateComment
  )
}

private class ResizableOutlineHandler private constructor(
  private val editor: EditorEx,
  private val inlayModel: CodeReviewInlayModel.Ranged.Adjustable,
  private val currentRange: LineRange,
  private val canCreateComment: (editorLine: Int) -> Boolean,
) : EditorMouseListener, EditorMouseMotionListener, VisibleAreaListener, DocumentListener, Disposable {
  private val resizeCursor = try {
    Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
  }
  catch (_: IllegalArgumentException) {
    Cursor.getDefaultCursor()
  }
  private val tooltipManager = OutlineTooltipManager(editor)
  private val dragState = MutableStateFlow<DragState?>(null)

  companion object {
    suspend fun showResizableOutline(
      editor: EditorEx,
      inlayModel: CodeReviewInlayModel.Ranged.Adjustable,
      inlayRenderer: CodeReviewComponentInlayRenderer,
      activeRangesTracker: CodeReviewActiveRangesTracker,
      initialRange: LineRange,
      canCreateComment: (editorLine: Int) -> Boolean,
    ): Nothing =
      withContext(Dispatchers.UiImmediate) {
        val handler = ResizableOutlineHandler(editor, inlayModel, initialRange, canCreateComment)
        try {
          editor.gutterComponentEx.mousePosition.let {
            handler.updateCursorAndTooltip(editor.gutterComponentEx, it)
            // set cursor to gutterComponentEx directly to get immediate cursor change
            // as setting it on the glass pane in `updateCursorAndTooltip()` doesn't work in this case
            editor.gutterComponentEx.setCursor(handler.resizeCursor)
          }

          handler.dragState.collectScoped { dragState ->
            if (dragState == null) {
              if(initialRange.end > DiffUtil.getLineCount(editor.document)-1) return@collectScoped
              inlayRenderer.isVisible = true
              editor.showOutline(activeRangesTracker, initialRange)
            }
            else {
              inlayRenderer.isVisible = false
              val newRange = when (dragState.edge) {
                LineRangeEdge.START -> initialRange.copy(startLine = dragState.line)
                LineRangeEdge.END -> initialRange.copy(endLine = dragState.line)
              }
              editor.showOutline(activeRangesTracker, newRange)
            }
          }
          awaitCancellation()
        }
        finally {
          Disposer.dispose(handler)
        }
      }
  }

  init {
    editor.addEditorMouseMotionListener(this)
    editor.addEditorMouseListener(this)
    editor.scrollingModel.addVisibleAreaListener(this)
    editor.document.addDocumentListener(this, this)
  }

  override fun dispose() {
    editor.removeEditorMouseListener(this)
    editor.removeEditorMouseMotionListener(this)
    editor.scrollingModel.removeVisibleAreaListener(this)
    updateCursorAndTooltip()
  }

  override fun mousePressed(e: EditorMouseEvent) {
    if (e.isConsumed || inlayModel.adjustmentDisabledReason.value != null) return
    val point = e.mouseEvent.point ?: return
    val edge = getEdgeAt(point) ?: return
    startDragging(edge)
    e.consume()
  }

  override fun mouseDragged(e: EditorMouseEvent) {
    if (e.isConsumed) return
    updateCursorAndTooltip(e.mouseEvent.component, e.mouseEvent.point)
    if (adjustDragRange(e.mouseEvent.y) == DragOutcome.SUCCESS) {
      e.consume() // to prevent selecting text while dragging
    }
  }

  override fun visibleAreaChanged(e: VisibleAreaEvent) {
    val oldRect = e.oldRectangle ?: return
    if (oldRect.y == e.newRectangle.y) return // No change in visible area, skip adjustment

    val y = editor.gutterComponentEx.mousePosition?.y ?: editor.contentComponent.mousePosition?.y
    if (adjustDragRange(y) == DragOutcome.INVALID) {
      finishDragging()
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    val y = editor.gutterComponentEx.mousePosition?.y ?: editor.contentComponent.mousePosition?.y
    if (adjustDragRange(y) == DragOutcome.INVALID) {
      finishDragging()
    }
  }

  override fun mouseReleased(e: EditorMouseEvent) {
    if (e.isConsumed) return
    if (finishDragging()) {
      e.consume()
    }
  }

  override fun mouseEntered(e: EditorMouseEvent) {
    updateCursorAndTooltip(e.mouseEvent.component, e.mouseEvent.point)
  }

  override fun mouseMoved(e: EditorMouseEvent) {
    updateCursorAndTooltip(e.mouseEvent.component, e.mouseEvent.point)
  }

  override fun mouseExited(e: EditorMouseEvent) {
    updateCursorAndTooltip()
  }

  private fun startDragging(edge: LineRangeEdge) {
    val line = currentRange.getLineAt(edge)
    dragState.value = DragState(edge, line)
  }

  private fun finishDragging(): Boolean {
    val state = dragState.getAndUpdate { null } ?: return false
    with(state) {
      if (currentRange.getLineAt(edge) != line) {
        when (edge) {
          LineRangeEdge.START -> inlayModel.adjustRange(newStart = line)
          LineRangeEdge.END -> inlayModel.adjustRange(newEnd = line)
        }
      }
    }
    updateCursorAndTooltip()
    return true
  }

  /**
   * Adjusts the ranges produced by dragging if dragging is in progress and [y] points to the commentable line in the editor.
   *
   * @return [DragOutcome] indicating the outcome of the adjustment
   */
  private fun adjustDragRange(y: Int?): DragOutcome {
    val currentState = dragState.value ?: return DragOutcome.UNCHANGED
    // `y == null` sometimes happens at deleting lines from the end of a document, while they are part of the drag range,
    // so to not process invalid range, drag has to be canceled
    if (y == null ||
        (currentState.edge == LineRangeEdge.END && currentState.line >= editor.document.lineCount) ||
        currentRange.end >= editor.document.lineCount) {
      dragState.value = null
      return DragOutcome.INVALID
    }
    val newState = currentState.withLineUnderYIfCommentable(y) ?: return DragOutcome.UNCHANGED

    if (!dragState.compareAndSet(currentState, newState)) {
      return DragOutcome.UNCHANGED
    }

    return if (currentState != newState) DragOutcome.SUCCESS else DragOutcome.UNCHANGED
  }

  private enum class DragOutcome {
    SUCCESS,
    UNCHANGED,
    INVALID
  }

  /**
   * Adjusts the [DragState.line] if [y] points to the commentable line in the editor depending on the [DragState.edge].
   *
   * @return null if the line under [y] is not commentable or the new range is invalid (start after end)
   */
  private fun DragState.withLineUnderYIfCommentable(y: Int): DragState? {
    val lineUnderY = editor.xyToLogicalPosition(Point(0, y)).line.coerceIn(0, DiffUtil.getLineCount(editor.document)-1)
    val isCurrentBoundary = lineUnderY == line
    if (ReviewInEditorUtil.isLastBlankLine(editor.document, lineUnderY)) return null
    if (!canCreateComment(lineUnderY) && !isCurrentBoundary) return null

    return when (edge) {
      LineRangeEdge.START -> {
        val newStart = lineUnderY.takeIf { it <= currentRange.end } ?: return null
        copy(line = newStart)
      }
      LineRangeEdge.END -> {
        val newEnd = lineUnderY.takeIf { it >= currentRange.start } ?: return null
        copy(line = newEnd)
      }
    }
  }

  private fun updateCursorAndTooltip(component: Component? = null, point: Point? = null) {
    if (component == null || point == null) {
      tooltipManager.hideTooltip()
      setEditorCursor(null)
      return
    }

    if (dragState.value != null) {
      tooltipManager.hideTooltip()
      setEditorCursor(resizeCursor)
      return
    }

    val onEdge = getEdgeAt(point) != null
    if (onEdge) {
      val adjustmentDisabledReason = inlayModel.adjustmentDisabledReason.value
      when (adjustmentDisabledReason) {
        AdjustmentDisabledReason.SUGGESTED_CHANGE -> {
          tooltipManager.showTooltip(component, point, OutlineTooltipManager.TooltipReason.SUGGESTION)
        }
        AdjustmentDisabledReason.SINGLE_COMMIT_REVIEW -> {
          tooltipManager.showTooltip(component, point, OutlineTooltipManager.TooltipReason.SINGLE_COMMIT_REVIEW)
        }
        else -> {
          tooltipManager.showTooltip(component, point, OutlineTooltipManager.TooltipReason.MLC_EXPLANATION)
          setEditorCursor(resizeCursor)
        }
      }
    }
    else {
      tooltipManager.hideTooltip()
      setEditorCursor(null)
    }
  }

  private fun getEdgeAt(point: Point): LineRangeEdge? {
    val yBorders = editor.yRangeForLogicalLineRange(currentRange.start, currentRange.end)
    val topY = yBorders.first.toFloat()
    val botY = yBorders.last.toFloat()

    if (point.x.toFloat() !in 0f..editor.contentComponent.width.toFloat()) return null
    if (point.y.toFloat() in (topY - OUTLINE_OUTSIDE_DETECTION_MARGIN).coerceAtLeast(0f)..topY + editor.lineHeight * OUTLINE_DETECTION_LINE_FRACTION) return LineRangeEdge.START
    if (point.y.toFloat() in botY - editor.lineHeight * OUTLINE_DETECTION_LINE_FRACTION..botY + OUTLINE_OUTSIDE_DETECTION_MARGIN) return LineRangeEdge.END

    return null
  }

  private fun setEditorCursor(cursor: Cursor?) {
    editor.setCustomCursor(this, cursor)
    // setting cursor in the gutter on glass pane, to avoid cursor changes from other sources while dragging
    try {
      IdeGlassPaneUtil.find(editor.gutterComponentEx).setCursor(cursor, this)
    }
    catch (_: Exception) {
    }
  }

  private data class DragState(val edge: LineRangeEdge, val line: Int)
}

private class OutlineTooltipManager(private val editor: Editor) {
  private var currentTooltip: IdeTooltip? = null

  fun showTooltip(component: Component, point: Point, tooltipReason: TooltipReason) {
    val offsetPoint = Point(point.x, point.y + editor.lineHeight) // offset for tooltip placement
    val tooltipMessage = TooltipReason.getTooltipMessage(tooltipReason)

    currentTooltip?.let {
      it.component = component
      it.point = offsetPoint
      it.tipComponent.toolTipText = tooltipMessage
    }

    if (currentTooltip == null) {
      val label = JLabel(TooltipReason.getTooltipMessage(tooltipReason))
      currentTooltip = IdeTooltip(component, offsetPoint, label)
        .setPreferredPosition(Balloon.Position.below)
        .setShowCallout(false)
    }
    IdeTooltipManager.getInstance().show(currentTooltip!!, false)
  }

  fun hideTooltip() {
    currentTooltip?.let {
      IdeTooltipManager.getInstance().hide(it)
    }
    currentTooltip = null
  }

  enum class TooltipReason {
    SUGGESTION,
    MLC_EXPLANATION,
    SINGLE_COMMIT_REVIEW;

    companion object {
      fun getTooltipMessage(tooltipReason: TooltipReason) = when (tooltipReason) {
        SUGGESTION -> CollaborationToolsBundle.message("review.comments.code.outline.tooltip.suggestion.disabling")
        MLC_EXPLANATION -> CollaborationToolsBundle.message("review.comments.code.outline.tooltip.explanation")
        SINGLE_COMMIT_REVIEW -> CollaborationToolsBundle.message("review.comments.code.outline.tooltip.commit.review.disabling")
      }
    }
  }
}

private fun JComponent.isFocusedOrHovered(): Flow<Boolean> {
  val component = this
  val inlayHovered = callbackFlow {
    Disposer.newDisposable("Focus listener").use { disposable ->
      component.addMouseHoverListener(disposable, object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          trySend(hovered)
        }
      })
      awaitClose()
    }
  }.flowOn(Dispatchers.UI).distinctUntilChanged()

  val inlayFocused = callbackFlow {
    Disposer.newDisposable("Focus listener").use { disposable ->
      FocusUtil.addFocusOwnerListener(disposable, PropertyChangeListener { evt ->
        if (evt.propertyName == "focusOwner") {
          trySend(Unit)
        }
      })
      awaitClose()
    }
  }.withInitial(Unit).map {
    UIUtil.isFocusAncestor(component)
  }.flowOn(Dispatchers.UI).distinctUntilChanged()

  return inlayHovered.combine(inlayFocused) { hovered, focused -> hovered || focused }
}

internal fun Editor.yRangeForLogicalLineRange(startLine: Int, endLine: Int): IntRange {
  val maxLine = (document.lineCount - 1).coerceAtLeast(0)
  val startOffset = document.getLineStartOffset(startLine.coerceIn(0, maxLine))
  val endOffset = document.getLineEndOffset(endLine.coerceIn(0, maxLine))

  val startY = offsetToXY(startOffset).y

  val foldRegion = foldingModel.getCollapsedRegionAtOffset(endOffset - 1).asSafely<CustomFoldRegion>()
  val endY = foldRegion?.location?.let { it.y + foldRegion.heightInPixels }
             ?: (offsetToXY(endOffset).y + lineHeight)

  return startY..endY
}

private class FadeLayerUI : LayerUI<JComponent>() {
  private var alpha: Float = 1f

  fun setAlpha(a: Float) {
    alpha = a.coerceIn(0f, 1f)
  }

  override fun paint(g: Graphics, c: JComponent) {
    val g2 = g.create() as Graphics2D
    try {
      val old = g2.composite
      g2.composite = AlphaComposite.SrcOver.derive(alpha)
      super.paint(g2, c)
      g2.composite = old
    }
    finally {
      g2.dispose()
    }
  }
}

interface CodeReviewActiveRangesTracker {
  val activeRanges: StateFlow<Collection<LineRange>>

  suspend fun rangeActivated(range: LineRange): Nothing
}

fun CodeReviewActiveRangesTracker(): CodeReviewActiveRangesTracker = ActiveRangesTrackerImpl()

private class ActiveRangesTrackerImpl : CodeReviewActiveRangesTracker {
  private val _activeRanges = MutableStateFlow(emptyList<LineRange>())
  override val activeRanges: StateFlow<Collection<LineRange>> = _activeRanges.asStateFlow()

  override suspend fun rangeActivated(range: LineRange): Nothing {
    try {
      _activeRanges.update { currentRange -> currentRange + range }
      awaitCancellation()
    }
    finally {
      _activeRanges.update { currentRange -> currentRange - range }
    }
  }
}

private enum class LineRangeEdge {
  START, END
}

private fun LineRange.getLineAt(edge: LineRangeEdge): Int =
  when (edge) {
    LineRangeEdge.START -> start
    LineRangeEdge.END -> end
  }

private fun LineRange.copy(startLine: Int? = null, endLine: Int? = null) = LineRange(startLine ?: this.start, endLine ?: this.end)