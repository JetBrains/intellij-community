// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookMarkdownCellLeftBorderRenderer
import com.intellij.notebooks.visualization.ui.EditorLayerController.Companion.getLayerController
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
) {  // PY-74106
  private var leftBorderHighlighter: RangeHighlighter? = null
  private var rightBorderLine: Line2D? = null
  private val frameColor = JBColor.LIGHT_GRAY

  fun updateMarkdownCellShow(selected: Boolean) {
    val layerController = editor.getLayerController()

    // draw or remove top and bottom lines with frame corners
    view.updateFrameVisibility(selected, frameColor)

    if (selected) {
      // add left and right sides of the border
      val startOffset = editor.document.getLineStartOffset(view.cell.interval.lines.first)
      val endOffset = editor.document.getLineEndOffset(view.cell.interval.lines.last)
      addLeftBorderHighlighter(startOffset, endOffset)
      updateRightBorder(layerController)
    } else {
      removeHighlighter(leftBorderHighlighter)
      leftBorderHighlighter = null
      removeRightBorder(layerController)
    }
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

  private fun updateRightBorder(layerController: EditorLayerController?) {
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

  private fun removeRightBorder(layerController: EditorLayerController?) {
    rightBorderLine?.let {
      layerController?.removeOverlayLine(it)
      rightBorderLine = null
    }
  }

  private fun removeHighlighter(highlighter: RangeHighlighter?) {
    highlighter?.let {
      editor.markupModel.removeHighlighter(it)
    }
  }

  fun dispose() {
    removeRightBorder(editor.getLayerController())
    removeHighlighter(leftBorderHighlighter)
    leftBorderHighlighter = null
  }

}
