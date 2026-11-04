package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;

public class RegularGridTableModel extends GridTableModel {
  public RegularGridTableModel(@NotNull GridModel<GridRow, GridColumn> gridModel) {
    super(gridModel);
  }

  @Override
  protected int row(int rowIndex, int columnIndex) {
    return rowIndex;
  }

  @Override
  protected int col(int rowIndex, int columnIndex) {
    return columnIndex;
  }

  @Override
  protected Range<Integer> rowRange(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns) {
    return getSmallestCoveringRange(rows);
  }


  @Override
  public int getRowCount() {
    return myGridModel.getRowCount();
  }

  @Override
  public int getColumnCount() {
    return myGridModel.getColumnCount();
  }


  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columns) {
    fireTableStructureChanged();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    fireTableStructureChanged();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    Range<Integer> rowRange = getSmallestCoveringRange(rows);
    fireTableRowsInserted(rowRange.getFrom(), rowRange.getTo());
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    Range<Integer> rowRange = getSmallestCoveringRange(rows);
    fireTableRowsDeleted(rowRange.getFrom(), rowRange.getTo());
  }
}
