// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookMarkdownCellCornerGutterLineMarkerRenderer
import com.intellij.openapi.editor.ex.RangeMarkerEx
import java.awt.Color

object NotebookCellInlayControllerUtil {
  fun NotebookCellInlayController.updateFrameVisibility(
    highlighter: RangeHighlighterEx?,
    isVisible: Boolean,
    startOffset: Int,
    endOffset: Int,
    frameColor: Color,
    position: NotebookMarkdownCellCornerGutterLineMarkerRenderer.Position
  ): RangeHighlighterEx? {
    val editor = inlay.editor as EditorEx

    return if (isVisible && highlighter == null) {
      val rangeMarkerId = (inlay as RangeMarkerEx).id

      editor.markupModel.addRangeHighlighterAndChangeAttributes(
        null,
        startOffset,
        endOffset,
        HighlighterLayer.FIRST - 100,
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
    } else if (!isVisible) {
      highlighter?.let { editor.markupModel.removeHighlighter(it) }
      null
    } else {
      highlighter
    }
  }
}
