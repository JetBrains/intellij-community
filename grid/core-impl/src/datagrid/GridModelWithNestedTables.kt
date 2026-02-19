package com.intellij.database.datagrid

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate
import com.intellij.database.datagrid.nested.NestedTablesAware

/**
 * The GridModelWithNestedTables interface represents a grid model that supports nested tables.
 * It extends the NestedTablesAware interface.
 */
interface GridModelWithNestedTables : NestedTablesAware<NestedTableCellCoordinate?> {
  /**
   * Checks whether nested tables support is enabled.
   *
   * @return `true` if nested tables support is enabled, `false` otherwise.
   */
  val isNestedTablesSupportEnabled: Boolean

  /**
   * Checks whether the current grid is a top-level grid or a nested grid.
   *
   * @return `true` if the grid is a top-level grid, `false` if it is a nested grid.
   */
  val isTopLevelGrid: Boolean

  /**
   * Navigates into a nested table based on the given cell coordinate.
   *
   * @param cellCoordinate the coordinate of the cell within the nested table
   *
   */
  @Deprecated("{@link GridModelWithNestedTables#enterNestedTable} instead.")
  fun navigateIntoNestedTable(cellCoordinate: NestedTableCellCoordinate)

  /**
   * Navigates back from a nested table by a specified number of steps.
   *
   * @param numSteps the number of steps to navigate back
   * @return the coordinate of the cell in the parent table after navigation back, or null if unable to navigate
   *
   */
  @Deprecated("This method is deprecated and marked for removal. Use {@link GridModelWithNestedTables#exitNestedTable} instead.")
  fun navigateBackFromNestedTable(numSteps: Int): NestedTableCellCoordinate?

  val pathToSelectedNestedTable: MutableList<NestedTableCellCoordinate>

  /**
   * Checks whether the specified column contains a nested table.
   *
   * @param column the column to check
   * @return `true` if the column contains a nested table, `false` otherwise
   */
  fun isColumnContainsNestedTable(column: GridColumn): Boolean

  /**
   * Retrieves the currently selected nested table.
   *
   * @return The currently selected nested table, or null if no nested table is selected.
   */
  val selectedNestedTable: NestedTable?

  override suspend fun enterNestedTable(coordinate: NestedTableCellCoordinate, nestedTable: NestedTable): NestedTableCellCoordinate {
    navigateIntoNestedTable(coordinate)
    return coordinate
  }

  override suspend fun exitNestedTable(steps: Int): NestedTableCellCoordinate? {
    return navigateBackFromNestedTable(steps)
  }
}
