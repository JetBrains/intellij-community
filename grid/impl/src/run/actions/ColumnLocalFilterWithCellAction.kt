package com.intellij.database.run.actions

import com.intellij.database.datagrid.*
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.table.LocalFilterState
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.actionSystem.AnActionEvent

class ColumnLocalFilterWithCellAction : ColumnHeaderActionBase(true) {
  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val gridModel = grid.getDataModel(DataAccessType.DATABASE_DATA)
    val columns = gridModel.getColumns(columnIdxs)

    e.presentation.isEnabledAndVisible =
      LocalFilterActionUtils.isGridInNonTransposedTableMode(grid) &&
      columns.size == 1 &&
      !GridHelper.get(grid).isColumnContainNestedTables(gridModel, columns[0])
  }


  override fun actionPerformed(e: AnActionEvent?, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    if (columnIdxs.size() != 1)
      return
    val resultView = grid.resultView
    if (resultView !is TableResultView)
      return

    val columnIdx = columnIdxs.first()
    val column = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx)
    if (column == null)
      return

    val rows = GridUtil.getSelectedGridRows(grid)
    resultView.localFilterState.clearFilterForColumn(columnIdx)
    rows.forEach { row ->
      val rowIdx = GridRow.toRealIdx(row)
      val text = GridUtil.getText(grid, ModelIndex.forRow(grid, rowIdx), columnIdx, DataAccessType.DATA_WITH_MUTATIONS)
      resultView.localFilterState.enableForColumn(columnIdx, LocalFilterState.Value(column.getValue(row), text))
    }

    resultView.localFilterState.isEnabled = true
    resultView.updateRowFilter()
    grid.panel.component.repaint()
  }
}
