package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TableModelEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.Arrays;

public abstract class GridTableModel extends AbstractTableModel implements GridModel.Listener<GridRow, GridColumn> {
  protected final DataGrid myGrid;

  public GridTableModel(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return myGrid.isEditable() && !GridUtil.isFailedToLoad(getValueAt(rowIndex, columnIndex));
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    throw new AssertionError("Modification is not supported!");
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    ModelIndex<GridRow> row = ModelIndex.forRow(myGrid, row(rowIndex, columnIndex));
    ModelIndex<GridColumn> column = ModelIndex.forColumn(myGrid, col(rowIndex, columnIndex));
    return myGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getValueAt(row, column);
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    if (columns.size() == 1 && rows.size() == 1) {
      int rowIndex = rows.asArray()[0];
      int colIndex = columns.asArray()[0];
      int row = row(rowIndex, colIndex);
      int column = col(rowIndex, colIndex);
      RequestedTableModelEvent event = new RequestedTableModelEvent(this, row, row, column, place);
      fireTableChanged(event);
    }
    else {
      Range<Integer> range = rowRange(rows, columns);
      RequestedTableModelEvent event =
        new RequestedTableModelEvent(this, range.getFrom(), range.getTo(), TableModelEvent.ALL_COLUMNS, TableModelEvent.UPDATE, place);
      fireTableChanged(event);
    }
  }


  protected abstract int row(int rowIndex, int columnIndex);

  protected abstract int col(int rowIndex, int columnIndex);

  protected abstract Range<Integer> rowRange(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns);

  public abstract @NotNull TableResultViewColumn createColumn(int columnDataIdx);

  static Range<Integer> getSmallestCoveringRange(ModelIndexSet<?> indexSet) {
    assert indexSet.size() > 0;

    int[] indices = indexSet.asArray();
    Arrays.sort(indices);
    return new Range<>(indices[0], indices[indices.length - 1]);
  }


  static class RequestedTableModelEvent extends TableModelEvent {
    private final GridRequestSource.RequestPlace myRequestor;

    RequestedTableModelEvent(TableModel source, int firstRow, int lastRow, int column, @Nullable GridRequestSource.RequestPlace requestor) {
      super(source, firstRow, lastRow, column);
      this.myRequestor = requestor;
    }

    RequestedTableModelEvent(TableModel source,
                             int firstRow,
                             int lastRow,
                             int column,
                             int type,
                             @Nullable GridRequestSource.RequestPlace requestor) {
      super(source, firstRow, lastRow, column, type);
      this.myRequestor = requestor;
    }

    @Nullable
    GridRequestSource.RequestPlace getPlace() {
      return myRequestor;
    }
  }
}
