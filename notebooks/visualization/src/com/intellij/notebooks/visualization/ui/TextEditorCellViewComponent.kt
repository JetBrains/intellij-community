package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.setMode
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.ui.providers.scroll.NotebookEditorScrollEndDetector
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class TextEditorCellViewComponent(private val cell: EditorCell) : EditorCellViewComponent(), InputComponent {
  private val editor: EditorEx
    get() = cell.editor

  private var highlighter: RangeHighlighter? = null

  // [MouseListener] is used instead of [EditorMouseListener] because the listener needs to be fired after caret positions were updated.
  private val mouseListener = object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      if (editor.xyToLogicalPosition(e.point).line in cell.interval.lines) {
        editor.setMode(NotebookEditorMode.EDIT)
      }
    }
  }

  private val presentationToInlay = mutableMapOf<InlayPresentation, Inlay<*>>()

  private val gutterIconStickToFirstVisibleLine
    get() = Registry.`is`("jupyter.run.cell.button.sticks.first.visible.line")

  private val coroutineScope = NotebookVisualizationCoroutine.Utils.edtScope.childScope("TextEditorCellViewComponent").also {
    Disposer.register(this, it.asDisposable())
  }

  init {
    editor.contentComponent.addMouseListener(mouseListener)
    Disposer.register(this) {
      editor.contentComponent.removeMouseListener(mouseListener)
    }

    cell.gutterAction.bind(this) {
      updateGutterIcons()
    }

    cell.isUnfolded.afterDistinctChange(this) { isUnfolded ->
      updateGutterIcons()
    }

    coroutineScope.launch {
      val detector = NotebookEditorScrollEndDetector.get(cell.editor)
      detector?.debouncedScrollFlow?.collect {
        updateGutterIcons()
      }
    }.cancelOnDispose(this)
  }

  private fun updateGutterIcons() {
    disposeExistingHighlighter()
    if (!cell.isUnfolded.get())
      return

    val gutterAction = cell.gutterAction.get() ?: return
    val interval = cell.intervalOrNull ?: return
    val markupModel = editor.markupModel
    val startOffset = editor.document.getLineStartOffset(interval.computeFirstLineForHighlighter(editor, gutterIconStickToFirstVisibleLine))
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val highlighter = markupModel.addRangeHighlighter(
      startOffset,
      endOffset,
      editor.notebookAppearance.cellBackgroundHighlightLayer,
      TextAttributes(),
      HighlighterTargetArea.LINES_IN_RANGE
    )

    highlighter.gutterIconRenderer = ActionToGutterRendererAdapter(gutterAction)
    this.highlighter = highlighter
  }

  override fun dispose(): Unit = editor.updateManager.update { ctx ->
    disposeExistingHighlighter()
  }

  private fun disposeExistingHighlighter() {
    val highlighter = highlighter ?: return
    this.highlighter = null
    editor.markupModel.removeHighlighter(highlighter)
    highlighter.dispose()
  }

  override fun calculateBounds(): Rectangle {
    val interval = cell.interval
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
    val interval = cell.interval
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

  override fun doCheckAndRebuildInlays() {
    if (isInlaysBroken()) {
      val presentations = presentationToInlay.keys.toList()
      presentationToInlay.values.forEach { inlay -> Disposer.dispose(inlay) }
      presentations.forEach { addInlayBelow(it) }
    }
  }

  private fun isInlaysBroken(): Boolean {
    val interval = cell.intervalOrNull ?: return true
    val offset = editor.document.getLineEndOffset(interval.lines.last)
    for (inlay in presentationToInlay.values) {
      if (!inlay.isValid || inlay.offset != offset) {
        return true
      }
    }
    return false
  }
}