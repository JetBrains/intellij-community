// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.frame

import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellLines.CellType
import com.intellij.notebooks.visualization.NotebookVisualizationCoroutine
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.getLayerController
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBColor
import com.intellij.util.asDisposable
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import java.awt.geom.Line2D
import java.time.Duration
import kotlin.time.toKotlinDuration

@OptIn(FlowPreview::class)
class EditorCellFrameManager(private val editorCell: EditorCell) : Disposable {  // PY-74106
  private val editor
    get() = editorCell.editor
  private val cellType
    get() = editorCell.interval.type
  private val view
    get() = editorCell.view

  private var leftBorderHighlighter: RangeHighlighter? = null
  private var rightBorderLine: Line2D? = null

  private val isSelected
    get() = editorCell.isSelected.get()
  private val isHovered
    get() = editorCell.isHovered.get()

  val state: AtomicProperty<CellFrameState> = AtomicProperty(CellFrameState())

  private val scope = NotebookVisualizationCoroutine.Utils.scope.childScope("Cell frame manager for ${editorCell.interval}").also {
    Disposer.register(this, it.asDisposable())
  }.also {
    Disposer.register(editorCell, it.asDisposable())
  }

  init {
    editorCell.isSelected.afterDistinctChange(this) {
      updateCellFrameShow()
    }
    editorCell.isHovered.afterDistinctChange(this) {
      updateCellFrameShow()
    }

    editorCell.isUnfolded.afterDistinctChange(this) {
      updateCellFrameShow()
    }

    updateCellFrameShow()

    scope.launch {
      JupyterBoundsChangeHandler.get(editor).eventFlow.debounce(Duration.ofMillis(200).toKotlinDuration()).collect {
        withContext(Dispatchers.EDT) {
          drawRightBorder()
        }
      }
    }.cancelOnDispose(this)
  }

  override fun dispose() {
    clearFrame()
  }

  fun calculateLineFrameVerticalLine(): Line2D? {
    val inlays = view?.input?.getBlockElementsInRange() ?: return null
    val upperInlayBounds = inlays.firstOrNull {
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY && it.properties.isShownAbove
    }?.bounds ?: return null

    val lowerInlayBounds = inlays.lastOrNull {
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY && !it.properties.isShownAbove
    }?.bounds ?: return null

    val lineX = upperInlayBounds.x + upperInlayBounds.width - 0.5
    val lineStartY = (upperInlayBounds.y + upperInlayBounds.height).toDouble()
    val lineEndY = (lowerInlayBounds.y).toDouble()

    val line2DDouble = Line2D.Double(lineX, lineStartY, lineX, lineEndY)
    return line2DDouble
  }

  private fun updateCellFrameShow() {
    if (cellType == CellType.MARKDOWN) {
      updateCellFrameShowMarkdown()
    }
    else {
      updateCellFrameShowCode()
    }
  }

  private fun updateCellFrameShowMarkdown() {
    if (editorCell.isUnderDiff.get()) {
      // under diff, it is necessary to make the selected cell more visible with blue frame for md cells
      updateCellFrameShowCode()
      return
    }

    when {
      isSelected -> drawFrame(editor.notebookAppearance.cellFrameSelectedColor.get())
      isHovered -> drawFrame(editor.notebookAppearance.cellFrameHoveredColor.get())
      else -> clearFrame()
    }
  }

  private fun updateCellFrameShowCode() {
    if (isSelected)
      drawFrame(editor.notebookAppearance.cellFrameSelectedColor.get())
    else
      clearFrame()
  }

  private fun drawFrame(color: Color) {
    state.set(CellFrameState(true, color))
    view?.updateFrameVisibility(true, color)
    drawLeftBorder()
    drawRightBorder()
  }

  private fun drawLeftBorder() {
    removeLeftBorder()

    val startOffset = editor.document.getLineStartOffset(editorCell.interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(editorCell.interval.lines.last)
    addLeftBorderHighlighter(startOffset, endOffset)
  }

  private fun drawRightBorder() {
    removeRightBorder()

    val frameState = state.get()
    if (!frameState.isVisible)
      return
    val color = frameState.color
    val line2DDouble = calculateLineFrameVerticalLine() ?: return
    val layerController = editor.getLayerController() ?: return
    layerController.addOverlayLine(line2DDouble, color)
    rightBorderLine = line2DDouble
  }


  private fun clearFrame() {
    state.set(CellFrameState(false))
    view?.updateFrameVisibility(false, JBColor.background())
    removeLeftBorder()
    removeRightBorder()
    val visibleArea = editor.scrollingModel.visibleArea
    editor.contentComponent.repaint(visibleArea)
  }

  private fun addLeftBorderHighlighter(startOffset: Int, endOffset: Int) {
    if (leftBorderHighlighter == null) {
      leftBorderHighlighter = editor.markupModel.addRangeHighlighterAndChangeAttributes(
        null,
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,
        HighlighterTargetArea.LINES_IN_RANGE,
        false
      ) { o: RangeHighlighterEx ->
        o.lineMarkerRenderer = NotebookCellLeftBorderRenderer(this)
      }
    }
  }

  private fun removeRightBorder() {
    val line = rightBorderLine ?: return
    val layerController = editor.getLayerController() ?: return
    layerController.removeOverlayLine(line)
    rightBorderLine = null
  }

  private fun removeLeftBorder() {
    val highlighter = leftBorderHighlighter ?: return
    editor.markupModel.removeHighlighter(highlighter)
    highlighter.dispose()
    leftBorderHighlighter = null
  }

  companion object {
    fun create(editorCell: EditorCell): EditorCellFrameManager? =
      if (editorCell.interval.type == CellType.MARKDOWN && Registry.`is`("jupyter.markdown.cells.border") ||
          Registry.`is`("jupyter.code.cells.border")) {
        EditorCellFrameManager(editorCell)
      }
      else {
        null
      }

  }
}