package com.intellij.database.datagrid.mutating;

import com.intellij.database.datagrid.GridColumn;
import org.jetbrains.annotations.NotNull;

/**
 * @author Liudmila Kornilova
 **/
public class ColumnMutation implements DatabaseMutation {
  private final GridColumn myColumn;

  public ColumnMutation(@NotNull GridColumn column) {
    myColumn = column;
  }

  @Override
  public int compareTo(@NotNull DatabaseMutation o) {
    return o instanceof ColumnMutation ? 0 : 1;
  }

  public @NotNull GridColumn getColumn() {
    return myColumn;
  }
}
