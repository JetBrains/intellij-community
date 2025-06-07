package com.intellij.database.datagrid;

import com.intellij.database.datagrid.mutating.ColumnDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GridColumn extends ColumnDescriptor {
  int getColumnNumber();

  default @Nullable Object getValue(@NotNull GridRow row) {
    return row.getValue(getColumnNumber());
  }
}
