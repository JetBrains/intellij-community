package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;

public class TransposedGridTableModel extends GridTableModel {
  public TransposedGridTableModel(@NotNull GridModel<GridRow, GridColumn> gridModel) {
    super(gridModel);
  }

  @Override
  protected int row(int rowIndex, int columnIndex) {
    return columnIndex;
  }

  @Override
  protected int col(int rowIndex, int columnIndex) {
    return rowIndex;
  }

  @Override
  protected Range<Integer> rowRange(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns) {
    return getSmallestCoveringRange(columns);
  }

  @Override
  public int getRowCount() {
    return myGridModel.getColumnCount();
  }

  @Override
  public int getColumnCount() {
    return myGridModel.getRowCount();
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columns) {
    Range<Integer> range = getSmallestCoveringRange(columns);
    fireTableRowsInserted(range.getFrom(), range.getTo());
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    Range<Integer> range = getSmallestCoveringRange(columns);
    fireTableRowsDeleted(range.getFrom(), range.getTo());
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    fireTableStructureChanged();
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    fireTableStructureChanged();
  }
}
