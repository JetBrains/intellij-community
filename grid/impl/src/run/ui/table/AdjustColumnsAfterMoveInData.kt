package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType
import kotlin.math.max
import kotlin.math.min

class AdjustColumnsAfterMoveInData(
  private val grid: DataGrid,
  private val tableView: TableResultView,
  private val from: Int,
  private val to: Int
) : () -> Unit {

  private val originallyVisibleColumns = grid.visibleColumns.asIterable().toSet()
  private val columnWidths = originallyVisibleColumns
    .mapTo(mutableListOf()) { modelIndex ->
      val width = tableView.getLayoutColumn(modelIndex)?.columnWidth ?: 0
      Pair(modelIndex, width)
    }
    .toMap()

  private val sortOrder = grid.getDataModel(DataAccessType.DATABASE_DATA).columns.associate {
    Pair(ModelIndex.forColumn(grid, it.columnNumber), grid.getSortOrder(it))
  }

  private val fromBeforeTo = from < to

  private fun toOriginalIndex(idx: Int): Int {
    val left = min(from, to)
    val right = max(from, to)
    if (idx < left || idx > right) {
      return idx
    }
    if (idx == to) {
      return from
    }
    return if (fromBeforeTo) idx + 1 else idx - 1
  }

  private fun toOriginalIndex(idx: ModelIndex<GridColumn>): ModelIndex<GridColumn> {
    return ModelIndex.forColumn(grid, toOriginalIndex(idx.value))
  }

  override fun invoke() {
    val columnIndices = grid.getDataModel(DataAccessType.DATABASE_DATA).getColumnIndices()

    // Show all columns for sorting
    for (columnIdx in columnIndices.asIterable()) {
      grid.setColumnEnabled(columnIdx, true)
    }

    // Reorder columns back as model indices command
    var currentOrder = grid.visibleColumns.asArray()
    val n = currentOrder.size
    for (i in 0 until n) {
      var min = Int.MAX_VALUE
      var argMin = -1
      for (j in i until n) {
        if (currentOrder[j] < min) {
          min = currentOrder[j]
          argMin = j
        }
      }

      if (argMin != -1 && argMin != i) {
        tableView.moveColumn(i, argMin)
        currentOrder = grid.visibleColumns.asArray()
      }
    }

    // Restore visibility
    for (columnIdx in columnIndices.asIterable()) {
      val originalIndex = toOriginalIndex(columnIdx)
      grid.setColumnEnabled(columnIdx, originallyVisibleColumns.contains(originalIndex))
    }

    // Restore widths
    grid.visibleColumns.asIterable().forEachIndexed { viewIndex, modelIndex ->
      val originalIndex = toOriginalIndex(modelIndex)
      tableView.getLayoutColumn(modelIndex)?.columnWidth = columnWidths[originalIndex]!!
    }

    // Restore attributes (sortOrder, filter, displayType)
    grid.getDataModel(DataAccessType.DATABASE_DATA).columns.forEach {
      val modelIndex = ModelIndex.forColumn(grid, it.columnNumber)
      val originalIndex = toOriginalIndex(modelIndex)
      grid.columnAttributes.setSortOrder(it, sortOrder[originalIndex] ?: 0)
    }
    grid.updateSortKeysFromColumnAttributes()
    grid.localFilterState.shiftColumns(columnIndices, this::toOriginalIndex)
    tableView.updateRowFilter()
  }
}