package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import com.intellij.database.datagrid.mutating.MutationData;
import com.intellij.database.run.ReservedCellValue;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class TypesMutationsStorageImpl<T> implements TypesMutationsStorage<T>, MutationsStorage {
  private final MutationsStorage myDelegate;
  private final GridModel<GridRow, GridColumn> myModel;

  public TypesMutationsStorageImpl(@NotNull MutationsStorage delegate, @NotNull GridModel<GridRow, GridColumn> model) {
    myDelegate = delegate;
    myModel = model;
  }

  @Override
  public void setType(@NotNull ModelIndex<GridRow> row,
                      @NotNull ModelIndex<GridColumn> column,
                      @Nullable T type) {
    if (!myDelegate.isValid(row, column)) return;
    MutationData mutationData = myDelegate.get(row, column);
    Object databaseValue = myModel.getValueAt(row, column);
    Object value = mutationData != null ? TypedValue.unwrap(mutationData.getValue()) :
                   myDelegate.isInsertedColumn(column) ? ReservedCellValue.UNSET :
                   databaseValue;
    if (type == null) {
      if (mutationData == null) return;
      myDelegate.set(row, column, new CellMutation(row, column, value));
    }
    else if (value instanceof ReservedCellValue || value == null) {
      ReservedCellValue notNullValue = (ReservedCellValue)ObjectUtils.notNull(value, ReservedCellValue.NULL);
      myDelegate.set(row, column, new CellMutation(row, column, new TypedValue<>(notNullValue, type)));
    }
  }

  @Override
  public @Nullable T getType(@NotNull ModelIndex<GridRow> row,
                             @NotNull ModelIndex<GridColumn> column) {
    MutationData data = myDelegate.get(row, column);
    //noinspection unchecked
    return data != null && data.getValue() instanceof TypedValue
           ? ((TypedValue<T>)data.getValue()).getType()
           : null;
  }

  @Override
  public void set(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @Nullable CellMutation value) {
    myDelegate.set(row, column, value == null ? null : new CellMutation(row, column, fixNewValue(value.getValue(), row, column)));
  }

  @Override
  public MutationData get(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (!myDelegate.isValid(row, column)) return null;
    MutationData data = myDelegate.get(row, column);
    if (data == null || !(data.getValue() instanceof TypedValue<?> value)) return data;
    return getFromDatabase(row, column) == value.getValue() ? null : new MutationData(value.getValue());
  }

  private @Nullable Object getFromDatabase(ModelIndex<GridRow> row, ModelIndex<GridColumn> column) {
    return myDelegate.isValid(row, column) ? myModel.getValueAt(row, column) : null;
  }

  private @Nullable Object fixNewValue(@Nullable Object value, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    if (!myDelegate.isValid(row, column)) return value;
    MutationData currentValue = myDelegate.get(row, column);
    return currentValue != null && currentValue.getValue() instanceof TypedValue && value instanceof ReservedCellValue
           ? new TypedValue<>((ReservedCellValue)value, ((TypedValue<?>)currentValue.getValue()).getType())
           : value;
  }

  @Override
  public void deleteRow(@NotNull ModelIndex<GridRow> rowIdx) {
    myDelegate.deleteRow(rowIdx);
  }

  @Override
  public boolean isModified(@NotNull ModelIndex<GridRow> row) {
    return myDelegate.isModified(row);
  }

  @Override
  public boolean isValid(@Nullable ModelIndex<GridRow> row, @Nullable ModelIndex<GridColumn> column) {
    return myDelegate.isValid(row, column);
  }

  @Override
  public boolean hasUnparsedValues() {
    return myDelegate.hasUnparsedValues();
  }

  @Override
  public boolean hasUnparsedValues(ModelIndex<GridRow> row) {
    return myDelegate.hasUnparsedValues(row);
  }

  @Override
  public boolean isInsertedRow(@NotNull ModelIndex<GridRow> row) {
    return myDelegate.isInsertedRow(row);
  }

  @Override
  public boolean isInsertedColumn(@NotNull ModelIndex<GridColumn> idx) {
    return myDelegate.isInsertedColumn(idx);
  }

  @Override
  public int getInsertedRowsCount() {
    return myDelegate.getInsertedRowsCount();
  }

  @Override
  public int getInsertedColumnsCount() {
    return myDelegate.getInsertedColumnsCount();
  }

  @Override
  public int getDeletedRowsCount() {
    return myDelegate.getDeletedRowsCount();
  }

  @Override
  public int getDeletedColumnsCount() {
    return myDelegate.getDeletedColumnsCount();
  }

  @Override
  public boolean isDeletedRow(@NotNull ModelIndex<GridRow> row) {
    return myDelegate.isDeletedRow(row);
  }

  @Override
  public boolean isDeletedColumn(@NotNull ModelIndex<GridColumn> column) {
    return myDelegate.isDeletedColumn(column);
  }

  @Override
  public boolean isDeletedRows(ModelIndexSet<GridRow> rows) {
    return myDelegate.isDeletedRows(rows);
  }

  @Override
  public @Nullable ModelIndex<GridRow> getLastInsertedRow() {
    return myDelegate.getLastInsertedRow();
  }

  @Override
  public void insertColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull GridColumn column) {
    myDelegate.insertColumn(idx, column);
  }

  @Override
  public void renameColumn(@NotNull ModelIndex<GridColumn> idx, @NotNull String newName) {
    myDelegate.renameColumn(idx, newName);
  }

  @Override
  public void removeColumnFromDeleted(@NotNull ModelIndex<GridColumn> index) {
    myDelegate.removeColumnFromDeleted(index);
  }

  @Override
  public void removeRowFromDeleted(@NotNull ModelIndex<GridRow> index) {
    myDelegate.removeRowFromDeleted(index);
  }

  @Override
  public @Nullable GridColumn getInsertedColumn(ModelIndex<GridColumn> idx) {
    return myDelegate.getInsertedColumn(idx);
  }

  @Override
  public @NotNull Set<ModelIndex<GridRow>> getModifiedRows() {
    return myDelegate.getModifiedRows();
  }

  @Override
  public void deleteColumn(@NotNull ModelIndex<GridColumn> columnIdx) {
    myDelegate.deleteColumn(columnIdx);
  }

  @Override
  public boolean hasChanges() {
    return myDelegate.hasChanges();
  }

  @Override
  public int getModifiedRowsCount() {
    return myDelegate.getModifiedRowsCount();
  }

  @Override
  public JBIterable<ModelIndex<GridRow>> getDeletedRows() {
    return myDelegate.getDeletedRows();
  }

  @Override
  public JBIterable<ModelIndex<GridColumn>> getDeletedColumns() {
    return myDelegate.getDeletedColumns();
  }

  @Override
  public JBIterable<ModelIndex<GridRow>> getInsertedRows() {
    return myDelegate.getInsertedRows();
  }

  @Override
  public JBIterable<ModelIndex<GridColumn>> getInsertedColumns() {
    return myDelegate.getInsertedColumns();
  }

  @Override
  public void insertRow(@NotNull ModelIndex<GridRow> row) {
    myDelegate.insertRow(row);
  }

  @Override
  public void clearRow(@NotNull ModelIndex<GridRow> rowIdx) {
    myDelegate.clearRow(rowIdx);
  }

  @Override
  public void clearColumns() {
    myDelegate.clearColumns();
  }
}
