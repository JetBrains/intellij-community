package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.text.lines

class TextEditorCellViewComponent(
  private val editor: EditorEx,
  private val cell: EditorCell,
) : EditorCellViewComponent(), InputComponent {

  private var highlighters: List<RangeHighlighter>? = null

  private val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  // todo: must be removed once we have a robust way to avoid getting interval for an invalid/deleted cell
  private val safeInterval: NotebookCellLines.Interval?
    get() = cell.intervalPointer.get()

  // [MouseListener] is used instead of [EditorMouseListener] because the listener needs to be fired after caret positions were updated.
  private val mouseListener = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      if (editor.xyToLogicalPosition(e.point).line in cell.interval.lines) {
        editor.setMode(NotebookEditorMode.EDIT)
      }
    }
  }

  private val presentationToInlay = mutableMapOf<InlayPresentation, Inlay<*>>()

  private val gutterIconStickToFirstVisibleLine = Registry.`is`("jupyter.run.cell.button.sticks.first.visible.line")

  init {
    editor.contentComponent.addMouseListener(mouseListener)
    cell.gutterAction.afterChange(this) { action ->
      updateGutterIcons(action)
    }
    updateGutterIcons(cell.gutterAction.get())
  }

  private fun updateGutterIcons(gutterAction: AnAction?) = runInEdt {
    disposeExistingHighlighter()
    if (gutterAction == null) return@runInEdt

    val markupModel = editor.markupModel
    val interval = safeInterval ?: return@runInEdt
    val startOffset = editor.document.getLineStartOffset(computeFirstLineForHighlighter(interval))
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val highlighter = markupModel.addRangeHighlighter(
      startOffset,
      endOffset,
      HighlighterLayer.FIRST - 100,
      TextAttributes(),
      HighlighterTargetArea.LINES_IN_RANGE
    )

    highlighter.gutterIconRenderer = ActionToGutterRendererAdapter(gutterAction)
    this.highlighters = listOf(highlighter)
  }

  override fun dispose() = cell.manager.update { ctx ->
    disposeExistingHighlighter()
    editor.contentComponent.removeMouseListener(mouseListener)
  }

  private fun disposeExistingHighlighter() {
    if (highlighters != null) {
      highlighters?.forEach {
        it.dispose()
      }
      highlighters = null
    }
  }

  private fun getFirstFullyVisibleLogicalLine(): Int? {
    val visibleArea = editor.scrollingModel.visibleArea
    val startY = visibleArea.y
    val endY = visibleArea.y + visibleArea.height

    val firstVisibleLine = editor.xyToLogicalPosition(Point(0, startY)).line
    val lastVisibleLine = editor.xyToLogicalPosition(Point(0, endY)).line

    for (line in firstVisibleLine..lastVisibleLine) {
      val lineStartY = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
      val lineEndY = lineStartY + editor.lineHeight
      if (lineStartY >= startY && lineEndY <= endY) {
        return line
      }
    }
    return null
  }

  private fun computeFirstLineForHighlighter(interval: NotebookCellLines.Interval): Int {
    return if (gutterIconStickToFirstVisibleLine) {
      val firstFullyVisibleLine = getFirstFullyVisibleLogicalLine()
      val startLine = if (firstFullyVisibleLine != null && firstFullyVisibleLine in interval.lines) {
        firstFullyVisibleLine
      } else {
        interval.lines.first
      }
      val fullyVisibleCell = getFirstFullyVisibleLogicalLine() == interval.lines.first
      if (fullyVisibleCell) interval.lines.first else startLine
    } else {
      interval.lines.first
    }
  }

  override fun calculateBounds(): Rectangle {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val startLocation = editor.offsetToXY(startOffset)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val endLocation = editor.offsetToXY(endOffset)
    val height = endLocation.y + editor.lineHeight - startLocation.y
    val width = endLocation.x - startLocation.x
    val dimension = Dimension(width, height)
    return Rectangle(startLocation, dimension)
  }

  override fun updateFolding(ctx: UpdateContext, folded: Boolean) {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val currentFoldingRegion = foldingModel.getFoldRegion(startOffset, endOffset)
    if (currentFoldingRegion == null) {
      ctx.addFoldingOperation { foldingModel ->
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val firstNotEmptyString = text.lines().firstOrNull { it.trim().isNotEmpty() }
        val placeholder = StringUtil.shortenTextWithEllipsis(firstNotEmptyString ?: "\u2026", 20, 0)
        foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, false)?.apply {
          FoldingModelImpl.hideGutterRendererForCollapsedRegion(this)
          isExpanded = false
        }
      }
    }
    else {
      ctx.addFoldingOperation { foldingModel ->
        if (currentFoldingRegion.isExpanded) {
          currentFoldingRegion.isExpanded = false
        }
        else {
          foldingModel.removeFoldRegion(currentFoldingRegion)
        }
      }
    }
  }

  override fun requestCaret() {
    val lines = cell.interval.lines
    val offset = editor.document.getLineStartOffset(lines.first + 1)
    editor.caretModel.moveToOffset(offset)
  }

  override fun addInlayBelow(presentation: InlayPresentation) {
    editor.inlayModel.addBlockElement(
      editor.document.getLineEndOffset(cell.interval.lines.last),
      InlayProperties()
        .showAbove(false)
        .showWhenFolded(true),
      PresentationRenderer(presentation)
    )?.also { inlay ->
      presentationToInlay[presentation] = inlay
      Disposer.register(this, inlay)
    }
  }

  override fun removeInlayBelow(presentation: InlayPresentation) {
    presentationToInlay.remove(presentation)?.let { inlay -> Disposer.dispose(inlay) }
  }

  override fun doGetInlays(): Sequence<Inlay<*>> {
    return presentationToInlay.values.asSequence()
  }

  override fun doCheckAndRebuildInlays() {
    if (isInlaysBroken()) {
      val presentations = presentationToInlay.keys.toList()
      presentationToInlay.values.forEach { inlay -> Disposer.dispose(inlay) }
      presentations.forEach { addInlayBelow(it) }
    }
  }

  private fun isInlaysBroken(): Boolean {
    val offset = editor.document.getLineEndOffset(interval.lines.last)
    for (inlay in presentationToInlay.values) {
      if (!inlay.isValid || inlay.offset != offset) {
        return true
      }
    }
    return false
  }

}