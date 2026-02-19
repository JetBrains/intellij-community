package com.intellij.database.datagrid;

import com.intellij.openapi.Disposable;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public interface GridModel<Row, Column> {
  boolean isValidRowIdx(@NotNull ModelIndex<Row> rowIdx);

  boolean isValidColumnIdx(@NotNull ModelIndex<Column> columnIdx);
  @Nullable
  Object getValueAt(ModelIndex<Row> row, ModelIndex<Column> column);

  boolean allValuesEqualTo(@NotNull ModelIndexSet<Row> rowIndices,
                           @NotNull ModelIndexSet<Column> columnIndices,
                           @Nullable Object what);

  default @Nullable HierarchicalReader getHierarchicalReader() {
    return null;
  }

  @Nullable
  Row getRow(@NotNull ModelIndex<Row> row);

  @Nullable
  Column getColumn(@NotNull ModelIndex<Column> column);

  @NotNull
  List<Row> getRows(@NotNull ModelIndexSet<Row> rows);

  @NotNull
  List<Column> getColumns(@NotNull ModelIndexSet<Column> columns);

  @NotNull
  List<Column> getColumns();

  @NotNull
  JBIterable<Column> getColumnsAsIterable();

  @NotNull
  JBIterable<Column> getColumnsAsIterable(@NotNull ModelIndexSet<Column> columns);

  @NotNull
  List<Row> getRows();

  @NotNull
  ModelIndexSet<Column> getColumnIndices();

  @NotNull
  ModelIndexSet<Row> getRowIndices();

  int getColumnCount();

  int getRowCount();

  boolean isUpdatingNow();

  void addListener(@NotNull Listener<Row, Column> l, @NotNull Disposable disposable);

  boolean hasListeners();

  default List<Column> getAllColumnsForExtraction(int... selectedColumns) {
    return getColumns();
  }

  interface Listener<Row, Column> extends EventListener {

    void columnsAdded(ModelIndexSet<Column> columns);

    void columnsRemoved(ModelIndexSet<Column> columns);

    void rowsAdded(ModelIndexSet<Row> rows);

    void rowsRemoved(ModelIndexSet<Row> rows);

    void cellsUpdated(ModelIndexSet<Row> rows, ModelIndexSet<Column> columns, @Nullable GridRequestSource.RequestPlace place);

    default void afterLastRowAdded() {
    }
  }
}
