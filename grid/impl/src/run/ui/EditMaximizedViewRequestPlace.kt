package com.intellij.database.run.ui

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRequestSource
import com.intellij.database.datagrid.GridRow

class EditMaximizedViewRequestPlace(private val grid: DataGrid, val viewer: CellViewer) : GridRequestSource.GridRequestPlace<GridRow, GridColumn> {
  override fun getGrid(): DataGrid = grid
}