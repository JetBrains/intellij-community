// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.ui.cell.background

import com.intellij.notebooks.ui.afterDistinctChange
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookCellHighlighterRenderer
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookCodeCellBackgroundLineMarkerRenderer
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.notebooks.visualization.controllers.selfUpdate.common.NotebookCellSelfHighlighterController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.TextAttributes

class CodeCellBackgroundController(editorCell: EditorCell) : NotebookCellSelfHighlighterController(editorCell) {
  init {
    editor.notebookAppearance.codeCellBackgroundColor.afterDistinctChange(this) {
      forceUpdate()
    }
  }

  override fun getHighlighterLayer(): Int = HighlighterLayer.FIRST - 100

  override fun getTextAttribute(): TextAttributes {
    val textAttributes = TextAttributes()
    textAttributes.backgroundColor = editor.notebookAppearance.codeCellBackgroundColor.get()
    return textAttributes
  }

  override fun customizeHighlighter(cellHighlighter: RangeHighlighterEx) {
    cellHighlighter.setCustomRenderer(NotebookCellHighlighterRenderer)
  }

  override fun createLineMarkerRender(rangeHighlighter: RangeHighlighterEx): NotebookLineMarkerRenderer? {
    // draws gray vertical rectangles between line numbers and the leftmost border of the text
    return NotebookCodeCellBackgroundLineMarkerRenderer(rangeHighlighter)
  }
}