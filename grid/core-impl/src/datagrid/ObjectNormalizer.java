package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ObjectNormalizer {
  @Nullable
  Object objectToObject(@Nullable Object o, GridColumn column);

  static void convertRows(@NotNull ObjectNormalizer normalizer, @NotNull List<? extends GridRow> rows, @Nullable List<? extends GridColumn> columns) {
    if (columns == null) return;
    for (GridRow row : rows) {
      if (row != null) {
        int size = Math.min(row.getSize(), columns.size());
        for (int i = 0; i < size; i++) {
          row.setValue(i, normalizer.objectToObject(row.getValue(i), columns.get(i)));
        }
      }
    }
  }
}
