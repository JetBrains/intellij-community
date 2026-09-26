package com.intellij.database.run.ui.grid

import com.intellij.database.datagrid.CoreGrid
import com.intellij.database.datagrid.GridColumn
import com.intellij.database.datagrid.GridRow

class MoveColumnsRequestPlace(
  grid: CoreGrid<GridRow, GridColumn>,
  loadingUI: () -> AutoCloseable,
  private val adjustColumnsUI: () -> Unit
) : LongActionRequestPlace(grid, loadingUI) {

  fun adjustColumnsUI() {
    adjustColumnsUI.invoke()
  }

}