// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI

object DefaultNotebookEditorAppearanceSizes : NotebookEditorAppearanceSizes {
  // TODO it's hardcoded, but it should be equal to distance between a folding line and an editor.
  override val CODE_CELL_LEFT_LINE_PADDING: Int = 5

  // TODO it's hardcoded, but it should be EditorGutterComponentImpl.getLineNumberAreaWidth()
  override val LINE_NUMBERS_MARGIN: Int = 10

  // TODO Do the pixel constants need JBUI.scale?
  override val COMMAND_MODE_CELL_LEFT_LINE_WIDTH: Int = JBUI.scale(4)
  override val EDIT_MODE_CELL_LEFT_LINE_WIDTH: Int = JBUI.scale(2)
  override val CODE_AND_CODE_TOP_GRAY_HEIGHT: Int = JBUI.scale(6)
  override val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT: Int = JBUI.scale(6)
  override val INNER_CELL_TOOLBAR_HEIGHT: Int = JBUI.scale(24)
  override val distanceBetweenCells: Int = JBUI.scale(16)
  override val cellBorderHeight: Int = JBUI.scale(16)
  override val aboveFirstCellDelimiterHeight: Int = JBUI.scale(42)
  override val SPACER_HEIGHT: Int = JBUI.scale(cellBorderHeight / 2)
  override val EXECUTION_TIME_HEIGHT: Int = JBUI.scale(SPACER_HEIGHT + 14)
  override val SPACE_BELOW_CELL_TOOLBAR: Int = JBUI.scale(4)
  override val CELL_TOOLBAR_TOTAL_HEIGHT: Int = JBUI.scale(INNER_CELL_TOOLBAR_HEIGHT + SPACE_BELOW_CELL_TOOLBAR)
  override val PROGRESS_STATUS_HEIGHT: Int = JBUI.scale(2)


  override val EXTRA_PADDING_EXECUTION_COUNT: Int = JBUI.scale(20)

  override fun getCellLeftLineWidth(editor: Editor): Int = EDIT_MODE_CELL_LEFT_LINE_WIDTH
  override fun getCellLeftLineHoverWidth(): Int = COMMAND_MODE_CELL_LEFT_LINE_WIDTH

  override fun getLeftBorderWidth(): Int =
    Integer.max(COMMAND_MODE_CELL_LEFT_LINE_WIDTH, EDIT_MODE_CELL_LEFT_LINE_WIDTH) + CODE_CELL_LEFT_LINE_PADDING
}