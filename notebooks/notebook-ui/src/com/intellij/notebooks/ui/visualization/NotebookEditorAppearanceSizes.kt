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


  val CODE_CELL_LEFT_LINE_PADDING: Int
  val LINE_NUMBERS_MARGIN: Int

  // TODO Do the pixel constants need JBUI.scale?
  val COMMAND_MODE_CELL_LEFT_LINE_WIDTH: Int
  val EDIT_MODE_CELL_LEFT_LINE_WIDTH: Int
  val CODE_AND_CODE_TOP_GRAY_HEIGHT: Int
  val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT: Int
  val INNER_CELL_TOOLBAR_HEIGHT: Int
  val SPACER_HEIGHT: Int
  val EXECUTION_TIME_HEIGHT: Int
  val SPACE_BELOW_CELL_TOOLBAR: Int
  val CELL_TOOLBAR_TOTAL_HEIGHT: Int
  val PROGRESS_STATUS_HEIGHT: Int


  val EXTRA_PADDING_EXECUTION_COUNT: Int
  val cellBorderHeight: Int
  val aboveFirstCellDelimiterHeight: Int
  val distanceBetweenCells: Int

  fun getCellLeftLineWidth(editor: Editor): Int
  fun getCellLeftLineHoverWidth(): Int
  fun getLeftBorderWidth(): Int
}