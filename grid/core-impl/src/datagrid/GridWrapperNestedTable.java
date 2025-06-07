package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

public class GridWrapperNestedTable implements NestedTable {
  private final GridListModelBase<GridRow, GridColumn> myGridModel;

  public GridWrapperNestedTable(GridListModelBase<GridRow, GridColumn> gridModel) {
    myGridModel = gridModel;
  }

  @Override
  public ColumnNamesHierarchyNode getColumnsHierarchy() {
    return null;
  }

  @Override
  public Object getValueAt(int rowIdx, int colIdx) {
    return myGridModel.getValueAt(ModelIndex.forRow(myGridModel, rowIdx), ModelIndex.forColumn(myGridModel, colIdx));
  }

  @Override
  public Object getValueAt(GridRow row, GridColumn column) {
    return myGridModel.getValueAt(row, column);
  }

  @Override
  public void setValueAt(int rowIdx, int colIdx, Object value) {
    GridRow row = myGridModel.getRow(ModelIndex.forRow(myGridModel, rowIdx));
    if (row == null) {
      throw new IllegalStateException("Unable to set value. No GridRow found at row index: " + rowIdx);
    }
    row.setValue(colIdx, value);
  }

  @Override
  public void addRow(GridRow value) {
    myGridModel.addRow(value);
  }

  @Override
  public int getRowsNum() {
    return myGridModel.getRowCount();
  }

  @Override
  public int getColumnsNum() {
    return myGridModel.getColumnCount();
  }

  @Override
  public boolean isValidRowIdx(int rowIdx) {
    return myGridModel.isValidRowIdx(ModelIndex.forRow(myGridModel, rowIdx));
  }

  @Override
  public boolean isValidColumnIdx(int colIdx) {
    return myGridModel.isValidColumnIdx(ModelIndex.forColumn(myGridModel, colIdx));
  }

  @Override
  public int getColumnType(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalStateException("Given column index " + colIdx + " is not valid.");
    }
    return Objects.requireNonNull(myGridModel.getColumn(ModelIndex.forColumn(myGridModel, colIdx))).getType();
  }

  @Override
  public String getColumnTypeName(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalStateException("Given column index " + colIdx + " is not valid.");
    }
    return Objects.requireNonNull(myGridModel.getColumn(ModelIndex.forColumn(myGridModel, colIdx))).getTypeName();
  }

  @Override
  public String getColumnName(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalStateException("Given column index " + colIdx + " is not valid.");
    }
    return Objects.requireNonNull(myGridModel.getColumn(ModelIndex.forColumn(myGridModel, colIdx))).getName();
  }

  @Override
  public @NotNull Iterator<Map<String, Object>> iterator() {
    return new JBIterator<>() {
      private int myNextValueIdx;

      @Override
      protected Map<String, Object> nextImpl() {
        return myNextValueIdx < getRowsNum() ? toMap(myNextValueIdx++) : stop();
      }
    };
  }

  private Map<String, Object> toMap(int rowIdx) {
    Map<String, Object> result = new HashMap<>();
    GridRow row = myGridModel.getRow(ModelIndex.forRow(myGridModel, rowIdx));
    for (GridColumn column : myGridModel.getColumns()) {
      result.put(column.getName(), getValueAt(row, column));
    }

    return result;
  }
}
