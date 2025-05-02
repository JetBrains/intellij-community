package com.intellij.notebooks.ui.editor

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.JBUI

class NewUINotebookDiffEditorAppearance(editor: Editor): DefaultNotebookEditorAppearance(editor){

  override fun shouldShowCellLineNumbers(): Boolean = false
  override fun shouldShowExecutionCounts(): Boolean = false  // not needed for DIFF -> execution does not reach it
  override fun shouldShowOutExecutionCounts(): Boolean = false
  override fun shouldShowRunButtonInGutter(): Boolean = true
  // see comments in org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
  override val CODE_CELL_LEFT_LINE_PADDING: Int = 5
  override val LINE_NUMBERS_MARGIN: Int = 10

  override val COMMAND_MODE_CELL_LEFT_LINE_WIDTH: Int = 4
  override val EDIT_MODE_CELL_LEFT_LINE_WIDTH: Int = 2
  override val CODE_AND_CODE_TOP_GRAY_HEIGHT: Int = 60
  override val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT: Int = 60
  override val INNER_CELL_TOOLBAR_HEIGHT: Int = 24
  override val cellBorderHeight: Int = JBUI.scale(20)
  override val distanceBetweenCells: Int = JBUI.scale(16)
  override val aboveFirstCellDelimiterHeight: Int = JBUI.scale(20)
  override val SPACER_HEIGHT: Int = cellBorderHeight / 2
  override val EXECUTION_TIME_HEIGHT: Int = 0  // not used in the jupyter diff viewer
  override val SPACE_BELOW_CELL_TOOLBAR: Int = 10
  override val CELL_TOOLBAR_TOTAL_HEIGHT: Int = INNER_CELL_TOOLBAR_HEIGHT + SPACE_BELOW_CELL_TOOLBAR
  override val PROGRESS_STATUS_HEIGHT: Int = 2


  override val EXTRA_PADDING_EXECUTION_COUNT: Int = 0

  override fun getCellLeftLineWidth(editor: Editor): Int = 10
  override fun getCellLeftLineHoverWidth(): Int = 10

  override fun getLeftBorderWidth(): Int =
    Integer.max(COMMAND_MODE_CELL_LEFT_LINE_WIDTH, EDIT_MODE_CELL_LEFT_LINE_WIDTH) + CODE_CELL_LEFT_LINE_PADDING
}
