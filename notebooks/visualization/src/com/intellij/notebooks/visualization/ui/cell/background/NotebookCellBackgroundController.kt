// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.background

import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookCellHighlighterRenderer
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.notebooks.visualization.controllers.selfUpdate.common.NotebookCellSelfHighlighterController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.TextAttributes

class NotebookCellBackgroundController(editorCell: EditorCell) : NotebookCellSelfHighlighterController(editorCell) {
  private var cachedBounds: Pair<Int, Int>? = null

  init {
    editor.notebookAppearance.codeCellBackgroundColor.afterDistinctChange(this) {
      forceUpdate()
    }
    val jupyterBoundsChangeHandler = JupyterBoundsChangeHandler.get(editor)
    jupyterBoundsChangeHandler.subscribe(this) {
      cachedBounds = null
    }
  }

  override fun getHighlighterLayer(): Int = editor.notebookAppearance.cellBackgroundHighlightLayer

  override fun getTextAttribute(): TextAttributes {
    val textAttributes = TextAttributes()
    textAttributes.backgroundColor = editor.notebookAppearance.codeCellBackgroundColor.get()
    return textAttributes
  }

  override fun customizeHighlighter(cellHighlighter: RangeHighlighterEx) {
    cellHighlighter.setCustomRenderer(NotebookCellHighlighterRenderer)
  }

  private fun calculateBounds(): Pair<Int, Int>? {
    val interval = editorCell.intervalOrNull ?: return null
    val range = interval.getCellRange(editor)
    val top = editor.offsetToXY(range.startOffset).y
    val height = editor.offsetToXY(range.endOffset).y + editor.lineHeight - top
    return top to height
  }

  fun getOrCalculateBounds(): Pair<Int, Int>? {
    if (cachedBounds != null)
      return cachedBounds

    val newBounds = calculateBounds()
    cachedBounds = newBounds
    return newBounds
  }

  override fun createLineMarkerRender(rangeHighlighter: RangeHighlighterEx): NotebookLineMarkerRenderer? {
    // draws gray vertical rectangles between line numbers and the leftmost border of the text
    return NotebookCodeCellBackgroundGutterRenderer(this)
  }
}