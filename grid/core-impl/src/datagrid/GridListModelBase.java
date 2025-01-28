package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.openapi.Disposable;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class GridListModelBase<Row, Column> implements GridModel<Row, Column> {

  private List<Column> myColumns;
  private final List<Row> myRows;
  private boolean myUpdatingNow;

  public GridListModelBase() {
    this(new ArrayList<>(), new ArrayList<>());
  }

  public GridListModelBase(List<Column> columns, List<Row> rows) {
    myColumns = columns;
    myRows = rows;
  }

  @Override
  public boolean hasListeners() {
    return false;
  }

  @Override
  public @Nullable Object getValueAt(ModelIndex<Row> rowIdx, ModelIndex<Column> columnIdx) {
    Row row = getRow(rowIdx);
    Column column = getColumn(columnIdx);
    return row != null && column != null ? getValueAt(row, column) : null;
  }

  @Override
  public @Nullable Row getRow(@NotNull ModelIndex<Row> row) {
    return getRow(row.asInteger());
  }

  @Override
  public @Nullable Column getColumn(@NotNull ModelIndex<Column> column) {
    return getColumn(column.asInteger());
  }

  @Override
  public @NotNull List<Row> getRows(@NotNull ModelIndexSet<Row> rows) {
    List<Row> result = new ArrayList<>(rows.size());
    for (int rowIndex : rows.asArray()) {
      result.add(getRow(rowIndex));
    }
    return result;
  }

  @Override
  public @NotNull List<Column> getColumns(@NotNull ModelIndexSet<Column> columns) {
    return getColumnsAsIterable(columns).toList();
  }

  @Override
  public @NotNull JBIterable<Column> getColumnsAsIterable(@NotNull ModelIndexSet<Column> columns) {
    return columns.asIterable().transform(this::getColumn);
  }

  @Override
  public @NotNull JBIterable<Column> getColumnsAsIterable() {
    return JBIterable.from(Collections.unmodifiableList(myColumns));
  }

  @Override
  public @NotNull List<Column> getColumns() {
    return Collections.unmodifiableList(myColumns);
  }

  @Override
  public @NotNull List<Row> getRows() {
    return Collections.unmodifiableList(myRows);
  }

  @Override
  public @NotNull ModelIndexSet<Column> getColumnIndices() {
    return ModelIndexSet.forColumns(this, range(0, getColumnCount()));
  }

  @Override
  public @NotNull ModelIndexSet<Row> getRowIndices() {
    return ModelIndexSet.forRows(this, range(0, getRowCount()));
  }

  @Override
  public int getColumnCount() {
    return myColumns.size();
  }

  @Override
  public int getRowCount() {
    return myRows.size();
  }

  @Override
  public boolean isUpdatingNow() {
    return myUpdatingNow;
  }

  @Override
  public boolean isValidRowIdx(@NotNull ModelIndex<Row> rowIdx) {
    return isValidIdx(myRows, rowIdx.asInteger());
  }

  @Override
  public boolean isValidColumnIdx(@NotNull ModelIndex<Column> columnIdx) {
    return isValidIdx(myColumns, columnIdx.asInteger());
  }

  @Override
  public void addListener(@NotNull Listener<Row, Column> l, @NotNull Disposable disposable) {
  }


  protected abstract @Nullable Object getValueAt(@NotNull Row row, @NotNull Column column);

  public abstract boolean allValuesEqualTo(@NotNull List<CellMutation> mutations);

  public void setUpdatingNow(boolean updatingNow) {
    myUpdatingNow = updatingNow;
  }

  public void addRows(@NotNull List<? extends Row> rows) {
    myRows.addAll(rows);
  }

  public void addRow(@NotNull Row row) {
    myRows.add(row);
  }

  public void removeRows(int firstRowIndex, int rowCount) {
    removeRange(myRows, firstRowIndex, rowCount);
  }

  public void setColumns(@NotNull List<? extends Column> columns) {
    myColumns.addAll(columns);
  }

  private Row getRow(int rowIdx) {
    return isValidIdx(myRows, rowIdx) ? myRows.get(rowIdx) : null;
  }

  private Column getColumn(int columnIdx) {
    return isValidIdx(myColumns, columnIdx) ? myColumns.get(columnIdx) : null;
  }

  public void clearColumns() {
    myColumns = new ArrayList<>();
  }

  public void set(int i, Row row) {
    myRows.set(i, row);
  }

  private static boolean isValidIdx(@NotNull List<?> list, int idx) {
    return idx > -1 && idx < list.size();
  }

  @SuppressWarnings("SameParameterValue")
  private static int[] range(int first, int length) {
    int[] range = new int[length];
    for (int i = 0; i < length; i++) {
      range[i] = first + i;
    }
    return range;
  }

  private static void removeRange(List<?> list, int firstIdx, int count) {
    for (int i = 0; i < count; i++) {
      list.remove(firstIdx + count - i - 1);
    }
  }
}
