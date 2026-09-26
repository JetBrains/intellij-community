package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.CoreGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow
import com.intellij.database.run.ui.DataGridRequestPlace

open class LongActionRequestPlace(
  grid: CoreGrid<GridRow, GridColumn>,
  val loadingUI: () -> AutoCloseable
) : DataGridRequestPlace(grid)