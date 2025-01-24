// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookMarkdownCellLeftBorderRenderer
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeListener
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.getLayerController
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.JBColor
import java.awt.geom.Line2D

class EditorCellFrameManager(
  private val editor: EditorImpl,
  private val view: EditorCellView,
): Disposable {  // PY-74106
  private var leftBorderHighlighter: RangeHighlighter? = null
  private var rightBorderLine: Line2D? = null
  private val frameColor = JBColor.LIGHT_GRAY
  private var isSelected = false

  private val boundsChangeListener = object : JupyterBoundsChangeListener {
    override fun boundsChanged() {
      if (isSelected) redrawBorders()
    }
  }

  init {
    JupyterBoundsChangeHandler.Companion.get(editor).subscribe(this, boundsChangeListener)
  }

  fun updateMarkdownCellShow(selected: Boolean) {
    isSelected = selected

    when (selected) {
      true -> redrawBorders()
      else -> clearFrame()
    }
  }

  private fun redrawBorders() {
    val layerController = editor.getLayerController()
    view.updateFrameVisibility(true, frameColor)

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
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY &&
      it.properties.isShownAbove == true }?.bounds ?: return

    val lowerInlayBounds = inlays.lastOrNull {
      it.properties.priority == editor.notebookAppearance.JUPYTER_CELL_SPACERS_INLAY_PRIORITY &&
      it.properties.isShownAbove == false }?.bounds ?: return

    val lineX = upperInlayBounds.x + upperInlayBounds.width - 0.5
    val lineStartY = (upperInlayBounds.y + upperInlayBounds.height).toDouble()
    val lineEndY = (lowerInlayBounds.y).toDouble()

    rightBorderLine = Line2D.Double(lineX, lineStartY, lineX, lineEndY).also {
      layerController.addOverlayLine(it, frameColor)
    }
  }

  private fun clearFrame() {
    view.updateFrameVisibility(false, frameColor)
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
        o.lineMarkerRenderer = NotebookMarkdownCellLeftBorderRenderer(o, color = frameColor)
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
    JupyterBoundsChangeHandler.Companion.get(editor).unsubscribe(boundsChangeListener)
    removeLeftBorder()
    removeRightBorder(editor.getLayerController())
  }

}
