package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public interface GridModelWithInjections<Row, Column> extends GridModel<Row, Column> {
  void injectValue(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> columns, @NotNull Object what);
}
