package com.intellij.database.datagrid;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;

public class GridSelectionImpl implements GridSelection<GridRow, GridColumn> {
  private final ModelIndexSet<GridRow> myRows;
  private ModelIndexSet<GridColumn> myColumns;
  private final @Nullable ModelIndex<GridRow> myCurrentRow;
  private final @Nullable ModelIndex<GridRow> myRangeStartRow;

  public GridSelectionImpl(@NotNull ModelIndexSet<GridRow> rows, @NotNull ModelIndexSet<GridColumn> columns) {
    this(rows, columns, null, null);
  }

  /** Restoring the rows alone moves the current row to the end of the interval, so it is stored with them. */
  public GridSelectionImpl(@NotNull ModelIndexSet<GridRow> rows,
                           @NotNull ModelIndexSet<GridColumn> columns,
                           @Nullable ModelIndex<GridRow> currentRow,
                           @Nullable ModelIndex<GridRow> rangeStartRow) {
    myRows = rows;
    myColumns = columns;
    myCurrentRow = currentRow;
    myRangeStartRow = rangeStartRow;
  }

  /**
   * A copy of {@code source} that selects {@code rows} and {@code columns}, and keeps the current row and the range
   * start row of {@code source}. Derive a selection from another one here, so that no caller drops those two by hand.
   */
  public static @NotNull GridSelectionImpl withSelection(@NotNull GridSelection<GridRow, GridColumn> source,
                                                         @NotNull ModelIndexSet<GridRow> rows,
                                                         @NotNull ModelIndexSet<GridColumn> columns) {
    return new GridSelectionImpl(rows, columns, source.getCurrentRow(), source.getRangeStartRow());
  }

  @Override
  public @Nullable ModelIndex<GridRow> getCurrentRow() {
    return myCurrentRow;
  }

  @Override
  public @Nullable ModelIndex<GridRow> getRangeStartRow() {
    return myRangeStartRow;
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
