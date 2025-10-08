package com.intellij.database.editor

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridDataHookUp
import com.intellij.database.datagrid.GridRow

/**
 * This interface returns a grid data management object from the editor.
 */
interface DataHookupContainer {
  val dataHookup: GridDataHookUp<GridRow, GridColumn>
}

/**
 * This interface returns a data grid component from the editor.
 */
interface DataGridContainer {
  val dataGrid: DataGrid
}