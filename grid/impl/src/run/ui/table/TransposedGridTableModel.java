package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class TransposedGridTableModel extends GridTableModel {
  public TransposedGridTableModel(@NotNull DataGrid grid) {
    super(grid);
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
    return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount();
  }

  @Override
  public int getColumnCount() {
    return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRowCount();
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

  @Override
  public @NotNull TableResultViewColumn createColumn(int columnDataIdx) {
    return new TransposedTableResultViewColumn(columnDataIdx);
  }

  protected class TransposedTableResultViewColumn extends TableResultViewColumn {
    public TransposedTableResultViewColumn(int modelIndex) {
      super(modelIndex);
    }

    @Override
    public Icon getIcon(boolean forDisplay) {
      return null;
    }

    @Override
    public @NlsContexts.ColumnName @NotNull String getHeaderValue() {
      return GridUtil.getRowName(myGrid, getModelIndex());
    }

    @Override
    public @NlsContexts.ColumnName @NotNull List<String> getMultilineHeaderValues() {
      return List.of(getHeaderValue());
    }

    @Override
    public @NlsContexts.Tooltip @Nullable String getTooltipText() {
      return null;
    }
  }
}
