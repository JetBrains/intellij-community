package com.intellij.database.datagrid;

import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.ColumnNamesHierarchyNode;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.dbimport.CsvImportUtil;
import com.intellij.database.dbimport.TypeMerger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.JBIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.database.datagrid.DocumentDataHookUp.DataMarkup.*;
import static com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.extractValueByHierarchicalIndex;
import static java.lang.Math.min;
import static java.lang.String.format;

public class DynamicNestedTable implements NestedTable {
  public static final int UNKNOWN_TOTAL_ROWS_NUM = -1;

  private final List<Row> myRows;

  private final ColumnNamesHierarchyNode myColumnsHierarchy;

  private int myTotalRowsNum = UNKNOWN_TOTAL_ROWS_NUM;

  public DynamicNestedTable(@NotNull List<Object[]> values, @NotNull ColumnNamesHierarchyNode hierarchy) {
    myRows = new ArrayList<>();
    for (int i = 0; i < values.size(); ++i) {
      myRows.add(new Row(i + 1, values.get(i)));
    }
    myColumnsHierarchy = hierarchy;
  }

  @Override
  public ColumnNamesHierarchyNode getColumnsHierarchy() {
    return myColumnsHierarchy;
  }

  @Override
  public Object getValueAt(int rowIdx, int colIdx) {
    return myRows.get(rowIdx).values[colIdx];
  }

  @Override
  public Object getValueAt(GridRow row, GridColumn column) {
    Object[] rowValues = myRows.get(GridRow.toRealIdx(row)).values;
    if (column instanceof HierarchicalGridColumn hierarchicalColumn) {
      return extractValueByHierarchicalIndex(rowValues, hierarchicalColumn.getPathFromRoot());
    }
    else {
      return rowValues[column.getColumnNumber()];
    }
  }

  @Override
  public void setValueAt(int rowIdx, int colIdx, Object value) {
    if (!isValidRowIdx(rowIdx) || !isValidColumnIdx(colIdx)) return;
    myRows.get(rowIdx).values[colIdx] = value;
  }

  @Override
  public int getRowsNum() {
    return myRows.size();
  }

  @Override
  public int getTotalRowsNum() {
    return myTotalRowsNum;
  }

  public void setTotalRowsNum(int totalRowsNum) {
    myTotalRowsNum = totalRowsNum;
  }

  @Override
  public int getColumnsNum() {
    return myColumnsHierarchy.getChildren().size();
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
      throw new IllegalArgumentException("Given column index " + colIdx + " is not valid.");
    }
    List<String> columnValues = getColumnValues(colIdx);
    TypeMerger merger = CsvImportUtil.getPreferredTypeMergerBasedOnContent(
      columnValues, STRING_MERGER, INTEGER_MERGER, BIG_INTEGER_MERGER, DOUBLE_MERGER, BOOLEAN_MERGER);

    return getType(merger);
  }

  private @NotNull List<String> getColumnValues(int colIdx) {
    List<String> columnValues = new ArrayList<>();
    for (int i = 0; i < min(200, myRows.size()); ++i) {
      Object value = myRows.get(i).values[colIdx];
      columnValues.add(value == null ? null : value.toString());
    }
    return columnValues;
  }

  @Override
  public String getColumnName(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalArgumentException("Given column index " + colIdx + " is not valid.");
    }

    return myColumnsHierarchy.getChildren().get(colIdx).getName();
  }

  @Override
  public String getColumnTypeName(int colIdx) {
    if (!isValidColumnIdx(colIdx)) {
      throw new IllegalArgumentException("Given column index " + colIdx + " is not valid.");
    }

    List<String> columnValues = getColumnValues(colIdx);
    TypeMerger merger = CsvImportUtil.getPreferredTypeMergerBasedOnContent(
      columnValues, STRING_MERGER, INTEGER_MERGER, BIG_INTEGER_MERGER, DOUBLE_MERGER, BOOLEAN_MERGER);

    return merger.getName();
  }

  @Override
  public void setRow(int rowIdx, GridRow row) {
    if (!isValidRowIdx(rowIdx)) {
      throw new IllegalArgumentException(
        format("Invalid given row index: %d. Index should be in range from 0 to %d", rowIdx, myRows.size() - 1)
      );
    }
    myRows.set(rowIdx, new Row(row.getRowNum(), GridRow.getValues(row)));
  }

  @Override
  public void addRow(@NotNull GridRow value) {
    myRows.add(new Row(value.getRowNum(), GridRow.getValues(value)));
  }

  @Override
  public void removeRow(int rowIdx) {
    myRows.remove(rowIdx);
  }

  @Override
  public int getRowNum(int modelRowIdx) {
    return myRows.get(modelRowIdx).rowNum;
  }

  @Override
  public void insertRows(int startingIdx, List<GridRow> rows) {
    if (startingIdx > getRowsNum()) {
      throw new IllegalArgumentException(
        "Starting index " +
        startingIdx +
        " is greater than the total number of rows " +
        getRowsNum() +
        ". Starting index should be less or equal to " +
        getRowsNum()
      );
    }

    // Correctly replace existing rows with new rows from the list
    int endIndex = min(startingIdx + rows.size(), getRowsNum());
    for (int i = startingIdx; i < endIndex; ++i) {
      setRow(i, rows.get(i - startingIdx)); // Use (i - startingIdx) to correctly index into 'rows'
    }

    // Add any remaining new rows beyond the existing table size
    if (rows.size() > getRowsNum() - startingIdx) {
      for (int i = endIndex; i < startingIdx + rows.size(); ++i) {
        addRow(rows.get(i - startingIdx)); // Continue adding remaining rows
      }
    }
  }

  public DynamicNestedTable shallowCopy() {
    DynamicNestedTable copy = new DynamicNestedTable(new ArrayList<>(), myColumnsHierarchy);
    copy.setTotalRowsNum(myTotalRowsNum);

    return copy;
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

  private @NotNull Map<String, Object> toMap(int rowIdx) {
    ColumnNamesHierarchyNode root = myColumnsHierarchy;

    Map<String, Object> result = new HashMap<>();
    for (int i = 0; i < root.getChildren().size(); ++i) {
      ColumnNamesHierarchyNode node = root.getChildren().get(i);
      dfs(rowIdx, node, result, new int[]{i});
    }

    return result;
  }

  private void dfs(int rowIdx, @NotNull ColumnNamesHierarchyNode node, Map<String, Object> result, int[] path) {
    if (node.getChildren().isEmpty()) {
      Object value = extractValueByHierarchicalIndex(myRows.get(rowIdx).values, path);
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

  private static class Row {
    public int rowNum;

    public Object[] values;

    Row(int rowNum, Object[] values) {
      this.rowNum = rowNum;
      this.values = values;
    }
  }
}