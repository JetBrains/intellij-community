package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface MutationsStorage {
  void set(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @Nullable CellMutation value);

  @Nullable MutationData get(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);

  void deleteRow(@NotNull ModelIndex<GridRow> rowIdx);

  boolean isModified(@NotNull ModelIndex<GridRow> row);

  boolean isValid(@Nullable ModelIndex<GridRow> row, @Nullable ModelIndex<GridColumn> column);

  boolean hasUnparsedValues();

  boolean hasUnparsedValues(ModelIndex<GridRow> row);

  boolean isInsertedRow(@NotNull ModelIndex<GridRow> row);

  boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> idx);

  int getInsertedRowsCount();

  int getInsertedColumnsCount();

  int getDeletedRowsCount();

  int getDeletedColumnsCount();

  boolean isDeletedRow(@NotNull ModelIndex<GridRow> row);

  boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> column);

  boolean isDeletedRows(ModelIndexSet<GridRow> rows);

  @Nullable ModelIndex<GridRow> getLastInsertedRow();

  void insertColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull GridColumn column);

  void renameColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull String newName);

  void removeColumnFromDeleted(@NotNull ModelIndex<GridColumn> index);

  void removeRowFromDeleted(@NotNull ModelIndex<GridRow> index);

  @Nullable GridColumn getInsertedColumn(ModelIndex<GridColumn> idx);

  @NotNull Set<ModelIndex<GridRow>> getModifiedRows();

  void deleteColumn(@NotNull ModelIndex<GridColumn> columnIdx);

  boolean hasChanges();

  int getModifiedRowsCount();

  JBIterable<ModelIndex<GridRow>> getDeletedRows();

  JBIterable<ModelIndex<GridColumn>> getDeletedColumns();

  JBIterable<ModelIndex<GridRow>> getInsertedRows();

  JBIterable<ModelIndex<GridColumn>> getInsertedColumns();

  void insertRow(@NotNull ModelIndex<GridRow> row);

  void clearRow(@NotNull ModelIndex<GridRow> rowIdx);

  void clearColumns();
}
