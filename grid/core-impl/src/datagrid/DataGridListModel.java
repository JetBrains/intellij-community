package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.CellMutation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

public class DataGridListModel extends GridListModelBase<GridRow, GridColumn> {
  private final BiFunction<Object, Object, Boolean> myValuesEquals;

  public DataGridListModel(@NotNull BiFunction<Object, Object, Boolean> valuesEquals) {
    myValuesEquals = valuesEquals;
  }

  @Override
  protected @Nullable Object getValueAt(@NotNull GridRow row, @NotNull GridColumn column) {
    return column.getValue(row);
  }

  @Override
  public boolean allValuesEqualTo(@NotNull ModelIndexSet<GridRow> rowIndices,
                                  @NotNull ModelIndexSet<GridColumn> columnIndices,
                                  @Nullable Object what) {
    for (ModelIndex<GridRow> rowIdx : rowIndices.asIterable()) {
      for (ModelIndex<GridColumn> colIdx : columnIndices.asIterable()) {
        if (!GridUtilCore.isRowId(getColumn(colIdx)) &&
            !myValuesEquals.apply(what, getValueAt(rowIdx, colIdx))) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public boolean allValuesEqualTo(@NotNull List<CellMutation> mutations) {
    for (CellMutation mutation : mutations) {
      ModelIndex<GridRow> row = mutation.getRow();
      ModelIndex<GridColumn> column = mutation.getColumn();
      Object value = mutation.getValue();
      Object oldValue = getValueAt(row, column);
      if (!GridUtilCore.isRowId(getColumn(column)) && !myValuesEquals.apply(value, oldValue)) return false;
    }
    return true;
  }
}
