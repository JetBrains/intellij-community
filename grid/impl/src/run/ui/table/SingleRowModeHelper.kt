 package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.ResultViewWithRows
import com.intellij.openapi.util.text.StringUtil
import kotlin.math.max
import kotlin.math.min

object SingleRowModeHelper {
  @JvmStatic
  private fun linesCount(grid: DataGrid): Int? {
    if (grid.resultView !is TableResultView) {
      return null
    }

    val model = grid.getDataModel(DataAccessType.DATABASE_DATA)
    if (model.rowCount == 1 && model.columnCount == 1) {
      val cellText = GridUtil.getText(
        grid,
        ViewIndex.forRow(grid, 0).toModel(grid),
        ViewIndex.forColumn(grid, 0).toModel(grid)
      )
      return max(1, StringUtil.getLineBreakCount(cellText) + 1)
    }
    return null
  }

  @JvmStatic
  fun expandRowIfNeeded(grid: DataGrid) {
    val singleRowLinesCount = linesCount(grid)
    if (singleRowLinesCount != null) {
      (grid.resultView as? TableResultView)?.let { view ->
        view.setRowHeight(0, singleRowLinesCount * view.rowHeight)
      }
    }
  }

  @JvmStatic
  fun calculateHeightForInEditorResult(grid: DataGrid, maxRows: Int): Int? {
    val count = linesCount(grid) ?: return null
    val rows = min(maxRows, count)
    return (grid.resultView as? ResultViewWithRows)?.rowHeight?.times(rows)
  }
}