// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookMarkdownCellCornerGutterLineMarkerRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import java.awt.Color

object NotebookCellInlayControllerUtil {
  fun NotebookCellInlayController.updateFrameVisibility(
    highlighter: RangeHighlighterEx?,
    isVisible: Boolean,
    startOffset: Int,
    endOffset: Int,
    frameColor: Color,
    position: NotebookMarkdownCellCornerGutterLineMarkerRenderer.Position,
  ): RangeHighlighterEx? {
    return updateFrameVisibility(inlay, highlighter, isVisible, startOffset, endOffset, frameColor, position)
  }

  fun updateFrameVisibility(
    inlay: Inlay<*>,
    highlighter: RangeHighlighterEx?,
    isVisible: Boolean,
    startOffset: Int,
    endOffset: Int,
    frameColor: Color,
    position: NotebookMarkdownCellCornerGutterLineMarkerRenderer.Position,
  ): RangeHighlighterEx? {
    if (highlighter != null) {
      inlay.editor.markupModel.removeHighlighter(highlighter)
      highlighter.dispose()
    }

    if (inlay.isValid.not() || !isVisible)
      return null

    val editor = inlay.editor as EditorEx
    val rangeMarkerId = (inlay as RangeMarkerEx).id

    return editor.markupModel.addRangeHighlighterAndChangeAttributes(
      null,
      startOffset,
      endOffset,
      HighlighterLayer.FIRST - 10,
      HighlighterTargetArea.LINES_IN_RANGE,
      false
    ) { o: RangeHighlighterEx ->
      o.lineMarkerRenderer = NotebookMarkdownCellCornerGutterLineMarkerRenderer(
        o,
        position,
        frameColor,
        rangeMarkerId
      )
    }
  }
}
