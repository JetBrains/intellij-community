package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.determineColumnType;
import static com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.extractValueByHierarchicalIndex;
import static java.lang.String.format;

public class StaticNestedTable implements NestedTable {
  private final Object[][] myRowsValues;

  private final ColumnNamesHierarchyNode myColumnsHierarchy;

  public StaticNestedTable(Object[][] values, ColumnNamesHierarchyNode hierarchy) {
    myRowsValues = values;
    myColumnsHierarchy = hierarchy;
  }

  @Override
  public ColumnNamesHierarchyNode getColumnsHierarchy() {
    return myColumnsHierarchy;
  }

  @Override
  public Object getValueAt(int rowIdx, int colIdx) {
    return myRowsValues[rowIdx][colIdx];
  }

  @Override
  public Object getValueAt(GridRow row, GridColumn column) {
    Object[] rowValues = myRowsValues[GridRow.toRealIdx(row)];
    if (column instanceof HierarchicalGridColumn hierarchicalColumn) {
      return extractValueByHierarchicalIndex(rowValues, hierarchicalColumn.getPathFromRoot());
    } else {
      return rowValues[column.getColumnNumber()];
    }
  }

  @Override
  public void setValueAt(int rowIdx, int colIdx, Object value) {
    if (!isValidRowIdx(rowIdx) || !isValidColumnIdx(colIdx)) return;
    myRowsValues[rowIdx][colIdx] = value;
  }

  @Override
  public int getRowsNum() {
    return myRowsValues.length;
  }

  @Override
  public int getColumnsNum() {
    return myRowsValues.length > 0 ? myRowsValues[0].length : 0;
  }

  @Override
  public boolean isValidRowIdx(int rowIdx) {
    return rowIdx > -1 && rowIdx < getRowsNum();
  }

  @Override
  public boolean isValidColumnIdx(int colIdx) {
    return colIdx > -1 && colIdx < getColumnsNum();
  }

  @Override
  public int getColumnType(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalStateException("Given column index " + colIdx + " is not valid.");
    }

    TypeMerger merger = determineColumnType(myRowsValues, new int[] { colIdx });
    return DocumentDataHookUp.DataMarkup.getType(merger);
  }

  @Override
  public String getColumnTypeName(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalStateException("Given column index " + colIdx + " is not valid.");
    }

    TypeMerger merger = determineColumnType(myRowsValues, new int[] { colIdx });
    return merger.getName();
  }

  @Override
  public String getColumnName(int colIdx) {
    return myColumnsHierarchy.getChildren().get(colIdx).getName();
  }

  @Override
  public void addRow(GridRow value) {
    throw new IllegalStateException(
      format("The %s does not support rows addition, use another implementation instead.", this.getClass().getSimpleName())
    );
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
    ColumnNamesHierarchyNode root = myColumnsHierarchy;

    Map<String, Object> result = new HashMap<>();
    for (int i = 0; i < root.getChildren().size(); ++i) {
      ColumnNamesHierarchyNode node = root.getChildren().get(i);
      dfs(rowIdx, node, result, new int[] {i});
    }

    return result;
  }

  private void dfs(int rowIdx, ColumnNamesHierarchyNode node, Map<String, Object> result, int[] path) {
    if (node.getChildren().isEmpty()) {
      Object value = extractValueByHierarchicalIndex(myRowsValues[rowIdx], path);
      result.put(node.getName(), value);
      return;
    }

    Map<String, Object> data = new HashMap<>();
    int[] updatedPath = ArrayUtil.append(path, 0);
    int lastIdx = updatedPath.length - 1;
    for (int idx = 0; idx < node.getChildren().size(); ++idx) {
      ColumnNamesHierarchyNode c = node.getChildren().get(idx);
      updatedPath[lastIdx] = idx;
      dfs(rowIdx, c, data, updatedPath);
    }

    result.put(node.getName(), data);
  }
}