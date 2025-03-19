package com.intellij.database.datagrid;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;

public class GridSelectionImpl implements GridSelection<GridRow, GridColumn> {
  private final ModelIndexSet<GridRow> myRows;
  private ModelIndexSet<GridColumn> myColumns;

  public GridSelectionImpl(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    myRows = rows;
    myColumns = columns;
  }

  @Override
  public void addSelectedColumns(@NotNull CoreGrid<GridRow, GridColumn> grid, @NotNull ModelIndexSet<GridColumn> additionalColumns) {
    myColumns = ModelIndexSet.forColumns(grid, merge(myColumns.asArray(), additionalColumns.asArray()));
  }

  private static int[] merge(int[] a, int[] b) {
    ArrayList<Integer> values = new ArrayList<>();
    for (int index : a) {
      values.add(index);
    }
    for (int index : b) {
      values.add(index);
    }
    ContainerUtil.removeDuplicates(values);
    Collections.sort(values);
    return ArrayUtil.toIntArray(values);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getSelectedRows() {
    return myRows;
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getSelectedColumns() {
    return myColumns;
  }
}
