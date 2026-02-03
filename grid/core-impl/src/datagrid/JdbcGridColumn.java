package com.intellij.database.datagrid;

import org.jetbrains.annotations.Nullable;

public interface JdbcGridColumn extends GridColumn, JdbcColumnDescriptor {
  @Nullable String getTable();

  @Nullable String getSchema();

  @Nullable String getCatalog();
}
