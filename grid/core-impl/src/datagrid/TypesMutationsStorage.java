package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Liudmila Kornilova
 **/
public interface TypesMutationsStorage<T> {
  void setType(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column, @Nullable T type);

  @Nullable
  T getType(@NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column);
}
