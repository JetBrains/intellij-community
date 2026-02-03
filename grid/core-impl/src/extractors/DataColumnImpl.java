package com.intellij.database.extractors;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.extensions.DataColumn;
import org.jetbrains.annotations.NotNull;

public class DataColumnImpl implements DataColumn {
  private final GridColumn myColumn;

  public DataColumnImpl(@NotNull GridColumn column) {
    myColumn = column;
  }

  @Override
  public int columnNumber() {
    return myColumn.getColumnNumber();
  }

  @Override
  public @NotNull String name() {
    return myColumn.getName();
  }

  public @NotNull GridColumn getColumn() {
    return myColumn;
  }

  public Object getValue(GridRow row) {
    return myColumn.getValue(row);
  }
}
