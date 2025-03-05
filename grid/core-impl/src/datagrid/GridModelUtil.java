package com.intellij.database.datagrid;

import org.jetbrains.annotations.Nullable;

public final class GridModelUtil {
  public static @Nullable Object tryToFindNonNullValueInColumn(@Nullable GridModel<GridRow, GridColumn> model,
                                                               @Nullable GridColumn column,
                                                               int numRowsToCheck) {
    if (model == null || column == null) return null;
    if (model.getRows().isEmpty()) return null;

    Object value = null;
    for (int rowIdx = 0; rowIdx < model.getRowCount() && rowIdx < numRowsToCheck && value == null; rowIdx++) {
      GridRow row = model.getRows().get(rowIdx);
      value = column.getValue(row);
    }

    return value;
  }
}
