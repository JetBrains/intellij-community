package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.run.ui.DataAccessType
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode

/**
 * This class lets set the same width for all columns.
 * In case, it's an index column the DefaultGridColumnLayout will be called.
 */
class EqualWidthGridColumnLayout(private val context: ColumnLayoutContext,
                                 val myGrid: DataGrid,
                                 val isEqualWidthColumn: (GridColumn) -> Boolean) : DefaultGridColumnLayout(context, myGrid) {

  override fun doLayout(columnDataIndices: List<ModelIndex<*>>): Boolean {
    val curStatisticsPanelMode = context.getStatisticsPanelMode()
    if (curStatisticsPanelMode == null || curStatisticsPanelMode == StatisticsPanelMode.OFF || context.isTransposed) {
      return super.doLayout(columnDataIndices)
    }

    for (ind in columnDataIndices) {
      val column = myGrid.getDataModel(DataAccessType.DATABASE_DATA).getColumn(ind as ModelIndex<GridColumn>)
      if (column != null && !isEqualWidthColumn(column)) {
        super.doLayout(listOf(ind))
      } else {
        context.getLayoutColumn(ind)?.columnWidth = MAX_COLUMN_WIDTH
      }
    }

    return true
  }

  companion object {
    const val MAX_COLUMN_WIDTH = 200
  }
}