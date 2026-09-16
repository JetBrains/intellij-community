package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GridSelection<Row, Column> {
  void addSelectedColumns(@NotNull CoreGrid<Row, Column> grid, @NotNull ModelIndexSet<Column> additionalColumns);

  @NotNull
  ModelIndexSet<Row> getSelectedRows();

  @NotNull
  ModelIndexSet<Column> getSelectedColumns();

  /** The row the user is on, or null when the selection carries none. */
  default @Nullable ModelIndex<Row> getCurrentRow() {
    return null;
  }

  /** The row the selected range started from, or null when the selection carries none. */
  default @Nullable ModelIndex<Row> getRangeStartRow() {
    return null;
  }
}
