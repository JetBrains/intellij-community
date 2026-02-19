package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public interface DataGridWithNestedTables {
  boolean isNestedTableSupportEnabled();

  boolean isTopLevelGrid();

  boolean onCellClick(@NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> colIdx);

  void onBreadCrumbClick(int x, int y);

  default boolean isNestedTableStatic() {
    return false;
  }
}
