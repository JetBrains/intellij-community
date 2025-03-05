package com.intellij.database.datagrid;

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate;
import com.intellij.database.datagrid.nested.NestedTablesAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The GridModelWithNestedTables interface represents a grid model that supports nested tables.
 * It extends the NestedTablesAware interface.
 */
public interface GridModelWithNestedTables extends NestedTablesAware<NestedTableCellCoordinate> {
  /**
   * Checks whether nested tables support is enabled.
   *
   * @return {@code true} if nested tables support is enabled, {@code false} otherwise.
   */
  boolean isNestedTablesSupportEnabled();

  /**
   * Checks whether the current grid is a top-level grid or a nested grid.
   *
   * @return {@code true} if the grid is a top-level grid, {@code false} if it is a nested grid.
   */
  boolean isTopLevelGrid();

  /**
   * Navigates into a nested table based on the given cell coordinate.
   *
   * @param cellCoordinate the coordinate of the cell within the nested table
   *
   * @deprecated {@link GridModelWithNestedTables#enterNestedTable} instead.
   */
  @Deprecated(forRemoval = true)
  void navigateIntoNestedTable(@NotNull NestedTableCellCoordinate cellCoordinate);

  /**
   * Navigates back from a nested table by a specified number of steps.
   *
   * @param numSteps the number of steps to navigate back
   * @return the coordinate of the cell in the parent table after navigation back, or null if unable to navigate
   *
   * @deprecated This method is deprecated and marked for removal. Use {@link GridModelWithNestedTables#exitNestedTable} instead.
   */
  @Deprecated(forRemoval = true)
  @Nullable
  NestedTableCellCoordinate navigateBackFromNestedTable(int numSteps);

  @NotNull
  List<NestedTableCellCoordinate> getPathToSelectedNestedTable();

  /**
   * Checks whether the specified column contains a nested table.
   *
   * @param column the column to check
   * @return {@code true} if the column contains a nested table, {@code false} otherwise
   */
  boolean isColumnContainsNestedTable(@NotNull GridColumn column);

  /**
   * Retrieves the currently selected nested table.
   *
   * @return The currently selected nested table, or null if no nested table is selected.
   */
  @Nullable NestedTable getSelectedNestedTable();

  @Override
  default NestedTableCellCoordinate enterNestedTable(@NotNull NestedTableCellCoordinate coordinate, @NotNull NestedTable nestedTable) {
    navigateIntoNestedTable(coordinate);
    return coordinate;
  }

  @Override
  default NestedTableCellCoordinate exitNestedTable(int steps) {
    return navigateBackFromNestedTable(steps);
  }
}
