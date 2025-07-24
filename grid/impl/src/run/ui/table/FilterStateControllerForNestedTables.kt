package com.intellij.database.run.ui.table

import com.intellij.database.datagrid.DataGrid
import com.intellij.database.datagrid.NestedTable
import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.database.datagrid.nested.NestedTablesAware
import com.intellij.database.datagrid.nested.NestedTablesAware.NonEmptyStack

class FilterStateControllerForNestedTables(private val grid: DataGrid): NestedTablesAware<LocalFilterState> {
  val activeFilterState: LocalFilterState
    get() = filterStateStack.last()

  private val filterStateStack: NonEmptyStack<LocalFilterState> = NonEmptyStack(LocalFilterState(grid))

  override suspend fun enterNestedTable(coordinate: NestedTableCellCoordinate, nestedTable: NestedTable): LocalFilterState {
    filterStateStack.push(LocalFilterState(grid, activeFilterState.isEnabled))
    grid.resultView.onLocalFilterStateChanged()

    return filterStateStack.last()
  }

  override suspend fun exitNestedTable(steps: Int): LocalFilterState {
    filterStateStack.pop(steps)
    grid.resultView.onLocalFilterStateChanged()

    return filterStateStack.last()
  }
}
