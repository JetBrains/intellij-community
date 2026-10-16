package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.datagrid.ModelIndex
import com.intellij.database.datagrid.ResultViewColumn
import com.intellij.database.datagrid.ViewIndex
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode

/** Provides frontend column measurements and widths. Call all methods on the EDT. */
interface ColumnLayoutContext {
  val isTransposed: Boolean
  fun getLayoutColumn(column: ModelIndex<*>): ResultViewColumn?
  fun getHeaderWidth(column: ResultViewColumn): Int
  fun getCellPreferredWidth(row: ViewIndex<GridRow>, column: ViewIndex<GridColumn>): Int
  fun getStatisticsPanelMode(): StatisticsPanelMode?
}
