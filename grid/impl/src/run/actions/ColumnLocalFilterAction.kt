package com.intellij.database.run.actions

import com.intellij.database.datagrid.*
import com.intellij.database.datagrid.GridUtil.getText
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.table.LocalFilterPopup
import com.intellij.database.run.ui.table.LocalFilterState
import com.intellij.database.run.ui.table.TableResultView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.popup.JBPopup

class ColumnLocalFilterAction : ColumnHeaderActionBase(true) {

  override fun update(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    val gridModel = grid.getDataModel(DataAccessType.DATABASE_DATA)
    val columns = gridModel.getColumns(columnIdxs)

    e.presentation.isEnabledAndVisible =
      LocalFilterActionUtils.isGridInNonTransposedTableMode(grid) &&
      columns.size == 1 &&
      !GridHelper.get(grid).isColumnContainNestedTables(gridModel, columns[0])
  }

  override fun actionPerformed(e: AnActionEvent, grid: DataGrid, columnIdxs: ModelIndexSet<GridColumn>) {
    if (columnIdxs.size() != 1)
      return

    val columnIdx = columnIdxs.first()
    createFilterPopup(grid, columnIdx)?.showInBestPositionFor(e.dataContext)
  }

  companion object {
    @JvmStatic
    fun createFilterPopup(grid: DataGrid, columnIdx: ModelIndex<GridColumn>): JBPopup? {
      val gridModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS)
      val column = gridModel.getColumn(columnIdx)
      if (column == null) {
        return null
      }

      val resultView = grid.resultView as? TableResultView
      if (resultView == null) {
        return null
      }

      val comparator = GridHelper.get(grid).createComparator(column)
      val formatterConfig = GridUtil.createFormatterConfig(grid, columnIdx)

      val rowsToShow = gridModel.getRowIndices().asIterable()
        .asSequence().filter { resultView.localFilterState.include(grid, it, columnIdx) }
      val allowedValuesForColumn = resultView.localFilterState.allowedValues(columnIdx)

      val itemGroups = rowsToShow
        .map { rowIdx ->
          val row = gridModel.getRow(rowIdx)
          if (row == null)
            return@map LocalFilterState.Value(null, GridUtil.NULL_TEXT)

          val text = getText(grid, row, column, formatterConfig)
          LocalFilterState.Value(column.getValue(row), text)

        }
        .groupByTo(mutableMapOf()) { x -> x }

      // We add values enabled for filter, which currently aren't present in the result set
      allowedValuesForColumn.forEach { v ->
        if (!itemGroups.contains(v)) {
          itemGroups[v] = mutableListOf()
        }
      }

      val items = itemGroups.mapTo(mutableListOf()) {
        LocalFilterPopup.ColumnItem(it.key, allowedValuesForColumn.contains(it.key), it.value.size)
      }
      items.sortWith(fun(o1: LocalFilterPopup.ColumnItem, o2: LocalFilterPopup.ColumnItem): Int {
        val state1 = o1.isSelected
        val state2 = o2.isSelected
        if (state1 != state2)
          return state2.compareTo(state1)

        if (comparator != null)
          return comparator.compareObjects(o1.value.obj, o2.value.obj)

        return o1.value.text.compareTo(o2.value.text)
      })

      resultView.localFilterState.isEnabled = true
      resultView.updateRowFilter()
      return LocalFilterPopup(grid, resultView, columnIdx, items, column.name).popup
    }
  }
}