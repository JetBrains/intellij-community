package com.intellij.database.run.ui;

import com.intellij.database.datagrid.*;
import org.jetbrains.annotations.NotNull;

public class DataGridRequestPlace implements GridRequestSource.GridRequestPlace<GridRow, GridColumn> {
  private final CoreGrid<GridRow, GridColumn> myGrid;
  private final ModelIndexSet<GridRow> myRows;
  private final ModelIndexSet<GridColumn> myColumns;

  public DataGridRequestPlace(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    this(grid, ModelIndexSet.forRows(grid), ModelIndexSet.forColumns(grid));
  }

  public DataGridRequestPlace(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    myGrid = grid;
    myRows = rows;
    myColumns = columns;
  }

  public @NotNull ModelIndexSet<GridRow> getRows() {
    return myRows;
  }

  public @NotNull ModelIndexSet<GridColumn> getColumns() {
    return myColumns;
  }

  @Override
  public @NotNull CoreGrid<GridRow, GridColumn> getGrid() {
    return myGrid;
  }
}