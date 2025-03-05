package com.intellij.database.run.ui.grid;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridColumnLayout;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndexSet;

public class DummyGridColumnLayout implements GridColumnLayout<GridRow, GridColumn> {
  @Override
  public boolean resetLayout() {
    return true;
  }

  @Override
  public void newColumnsAdded(ModelIndexSet<GridColumn> columnIndices) {
  }

  @Override
  public void newRowsAdded(ModelIndexSet<GridRow> rowIndices) {
  }

  @Override
  public void columnsShown(ModelIndexSet<?> columnDataIndices) {
  }

  @Override
  public void invalidateCache() {
  }
}
