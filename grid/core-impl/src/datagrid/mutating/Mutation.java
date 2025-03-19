package com.intellij.database.datagrid.mutating;

import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndex;
import org.jetbrains.annotations.NotNull;

public abstract class Mutation {
  private final ModelIndex<GridRow> myRow;

  protected Mutation(@NotNull ModelIndex<GridRow> row) {
    myRow = row;
  }

  public @NotNull ModelIndex<GridRow> getRow() {
    return myRow;
  }
}
