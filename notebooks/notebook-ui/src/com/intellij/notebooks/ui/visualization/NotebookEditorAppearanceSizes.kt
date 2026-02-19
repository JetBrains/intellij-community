// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer

interface NotebookEditorAppearanceSizes {
  val cellBackgroundHighlightLayer: Int
    get() = HighlighterLayer.FIRST - 100
  val cellBorderHighlightLayer: Int
    get() = cellBackgroundHighlightLayer + 1

  val cellInputInlaysPriority: Int
    get() = 10
  val jupyterBelowLastCellInlayPriority: Int
    get() = -20
  val cellToolbarInlayPriority: Int
    get() = cellInputInlaysPriority + 10
  val cellOutputToolbarInlayPriority: Int
    get() = 5

  val codeCellLeftLinePadding: Int
  val lineNumbersMargin: Int
  val commandModeCellLeftLineWidth: Int
  val editModeCellLeftLineWidth: Int
  val codeAndCodeTopGrayHeight: Int
  val codeAndCodeBottomGrayHeight: Int
  val innerCellToolbarHeight: Int
  val spacerHeight: Int
  val executionTimeHeight: Int
  val spaceBelowCellToolbar: Int
  val cellToolbarTotalHeight: Int
  val progressStatusHeight: Int

  val extraPaddingExecutionCount: Int
  val cellBorderHeight: Int
  val aboveFirstCellDelimiterHeight: Int
  val distanceBetweenCells: Int

  fun getCellLeftLineWidth(editor: Editor): Int
  fun getCellLeftLineHoverWidth(): Int
  fun getLeftBorderWidth(): Int
}