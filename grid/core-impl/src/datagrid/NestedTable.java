package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;

import java.util.List;
import java.util.Map;

public interface NestedTable extends Iterable<Map<String, Object>> {
  ColumnNamesHierarchyNode getColumnsHierarchy();

  Object getValueAt(int rowIdx, int colIdx);

  Object getValueAt(GridRow row, GridColumn column);

  void setValueAt(int rowIdx, int colIdx, Object value);

  int getRowsNum();

  default int getTotalRowsNum() {
    return getRowsNum();
  }

  int getColumnsNum();

  boolean isValidRowIdx(int rowIdx);

  boolean isValidColumnIdx(int colIdx);

  int getColumnType(int colIdx);

  String getColumnTypeName(int colIdx);

  String getColumnName(int colIdx);

  default void setRow(int rowIdx, GridRow value) { }

  void addRow(GridRow value);

  default void removeRow(int rowIdx) { }

  default int getRowNum(int modelRowIdx) {
    return modelRowIdx + 1;
  }

  default void insertRows(int startingIdx, List<GridRow> rows) { }
}
