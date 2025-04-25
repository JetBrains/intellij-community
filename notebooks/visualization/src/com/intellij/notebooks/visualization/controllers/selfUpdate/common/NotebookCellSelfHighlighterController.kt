// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers.selfUpdate.common

import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.Consumer

abstract class NotebookCellSelfHighlighterController(
  val editorCell: EditorCell,
) : SelfManagedCellController {
  private var highlighter: RangeHighlighterEx? = null

  protected val editor: EditorImpl
    get() = editorCell.editor

  private val interval
    get() = editorCell.interval

  override fun dispose() {
    disposeHighlighter()
  }

  open fun getHighlighterLayer(): Int = HighlighterLayer.FIRST
  open fun createLineMarkerRender(rangeHighlighter: RangeHighlighterEx): NotebookLineMarkerRenderer? = null
  open fun getTextAttribute(): TextAttributes? = null
  open fun customizeHighlighter(cellHighlighter: RangeHighlighterEx) {}

  override fun selfUpdate() {
    if (highlighter?.isValid == true &&
        highlighter?.startOffset == editorCell.interval.getCellStartOffset(editor) &&
        highlighter?.endOffset == editorCell.interval.getCellEndOffset(editor)
    ) {
      //No need to update highlighter
      return
    }
    forceUpdate()
  }

  fun forceUpdate() {
    disposeHighlighter()
    createNewHighlighter()
  }

  private fun disposeHighlighter() {
    val highlighterEx = highlighter ?: return
    editor.markupModel.removeHighlighter(highlighterEx)
    highlighterEx.dispose()
    highlighter = null
  }


  private fun createNewHighlighter() {
    val startOffset = interval.getCellStartOffset(editor)
    val endOffset = interval.getCellEndOffset(editor)
    val changeAction = Consumer { rangeHighlighter: RangeHighlighterEx ->
      rangeHighlighter.lineMarkerRenderer = createLineMarkerRender(rangeHighlighter)

    }

    @Suppress("DEPRECATION")
    val highlighter = editor.markupModel.addRangeHighlighterAndChangeAttributes(startOffset, endOffset,
                                                                                getHighlighterLayer(),
                                                                                getTextAttribute(),
                                                                                HighlighterTargetArea.LINES_IN_RANGE,
                                                                                false,
                                                                                changeAction)
    highlighter.isGreedyToRight = true
    customizeHighlighter(highlighter)
    this.highlighter = highlighter
  }
}