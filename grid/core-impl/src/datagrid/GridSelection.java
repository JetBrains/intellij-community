package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;

public interface GridSelection<Row, Column> {
  void addSelectedColumns(@NotNull CoreGrid<Row, Column> grid, @NotNull ModelIndexSet<Column> additionalColumns);

  @NotNull
  ModelIndexSet<Row> getSelectedRows();

  @NotNull
  ModelIndexSet<Column> getSelectedColumns();
}
