// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.frame

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookMarkdownCellLeftBorderRenderer
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.ui.EditorCellView
import com.intellij.notebooks.visualization.ui.EditorLayerController
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.getLayerController
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.observable.properties.AtomicProperty
import java.awt.Color
import java.awt.geom.Line2D

class EditorCellFrameManager(
  private val editor: EditorImpl,
  private val view: EditorCellView,
  private val cellType: NotebookCellLines.CellType,
) : Disposable {  // PY-74106
  private var leftBorderHighlighter: RangeHighlighter? = null
  private var rightBorderLine: Line2D? = null
  private var currentColor: Color = editor.notebookAppearance.cellFrameHoveredColor.get()

  private var isSelected = false
  private var isHovered = false

  val state: AtomicProperty<CellFrameState> = AtomicProperty(CellFrameState())

  init {
    if (cellType == NotebookCellLines.CellType.CODE) redrawBorders(editor.notebookAppearance.cellFrameHoveredColor.get())
  }

  fun redrawBorders(): Unit = redrawBorders(currentColor)

  fun updateCellFrameShow(selected: Boolean, hovered: Boolean) {
    isSelected = selected
    isHovered = hovered

    when (cellType) {
      NotebookCellLines.CellType.MARKDOWN -> updateCellFrameShowMarkdown()
      NotebookCellLines.CellType.CODE -> updateCellFrameShowCode()
      else -> {}
    }
  }

  private fun updateCellFrameShowMarkdown() {
    if (view.isUnderDiff) {
      // under diff, it is necessary to make the selected cell more visible with blue frame for md cells
      updateCellFrameShowCode()
      return
    }

    when {
      isSelected -> redrawBorders(editor.notebookAppearance.cellFrameSelectedColor.get())
      isHovered -> redrawBorders(editor.notebookAppearance.cellFrameHoveredColor.get())
      else -> clearFrame()
    }
  }

  private fun updateCellFrameShowCode() {
    when (isSelected) {
      true -> redrawBorders(editor.notebookAppearance.cellFrameSelectedColor.get())
      else -> redrawBorders(editor.notebookAppearance.cellFrameHoveredColor.get())
    }
  }

  private fun redrawBorders(color: Color) {
    currentColor = color

    val layerController = editor.getLayerController()
    state.set(CellFrameState(true, currentColor))
    view.updateFrameVisibility(true, currentColor)

    redrawLeftBorder()
    redrawRightBorder(layerController)
  }

  private fun redrawLeftBorder() {
    removeLeftBorder()

    val startOffset = editor.document.getLineStartOffset(view.cell.interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(view.cell.interval.lines.last)
    addLeftBorderHighlighter(startOffset, endOffset)
  }

  private fun redrawRightBorder(layerController: EditorLayerController?) {
    layerController ?: return
    removeRightBorder(layerController)

    val inlays = view.input.getBlockElementsInRange()
    val upperInlayBounds = inlays.firstOrNull {
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY && it.properties.isShownAbove
    }?.bounds ?: return

    val lowerInlayBounds = inlays.lastOrNull {
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY && !it.properties.isShownAbove
    }?.bounds ?: return

    val lineX = upperInlayBounds.x + upperInlayBounds.width - 0.5
    val lineStartY = (upperInlayBounds.y + upperInlayBounds.height).toDouble()
    val lineEndY = (lowerInlayBounds.y).toDouble()

    rightBorderLine = Line2D.Double(lineX, lineStartY, lineX, lineEndY).also {
      layerController.addOverlayLine(it, currentColor)
    }
  }

  private fun clearFrame() {
    state.set(CellFrameState(false, currentColor))
    view.updateFrameVisibility(false, currentColor)
    removeLeftBorder()
    removeRightBorder(editor.getLayerController())
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
        o.lineMarkerRenderer = NotebookMarkdownCellLeftBorderRenderer(o, color = currentColor)
        { view.input.component.calculateBounds().let { it.y to it.height } }
      }
    }
  }

  private fun removeRightBorder(layerController: EditorLayerController?) {
    rightBorderLine?.let {
      layerController?.removeOverlayLine(it)
      rightBorderLine = null
    }
  }

  private fun removeLeftBorder() {
    leftBorderHighlighter?.let {
      editor.markupModel.removeHighlighter(it)
      leftBorderHighlighter = null
    }
  }

  override fun dispose() {
    removeLeftBorder()
    removeRightBorder(editor.getLayerController())
  }
}