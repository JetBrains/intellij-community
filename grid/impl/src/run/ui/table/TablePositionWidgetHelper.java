package com.intellij.database.run.ui.table;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.wm.impl.status.PositionPanel;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

import static com.intellij.database.datagrid.GridPositionWidget.POSITION_WIDGET_HELPER_KEY;

public class TablePositionWidgetHelper implements GridWidget.GridWidgetHelper {
  private final TableResultView myTable;
  private final DataGrid myGrid;

  public TablePositionWidgetHelper(@NotNull TableResultView table, @NotNull DataGrid grid) {
    myTable = table;
    myGrid = grid;
    table.putClientProperty(POSITION_WIDGET_HELPER_KEY, this);
  }

  @Override
  public @NotNull CompletableFuture<@NlsContexts.Label String> getText() {
    @NlsSafe
    StringBuilder sb = new StringBuilder();
    SelectionModel<GridRow, GridColumn> model = myGrid.getSelectionModel();
    int colNum = model.getSelectedColumnCount();
    int rowNum = model.getSelectedRowCount();
    if (colNum == 0 || rowNum == 0) return CompletableFuture.completedFuture("");


    boolean editing = myGrid.isEditing();
    if (colNum > 1 || rowNum > 1) {
      int cellCount = colNum * rowNum;
      sb.append(cellCount).append(" ")
        .append(DataGridBundle.message("TablePositionWidgetHelper.cell", Math.min(cellCount, 2)))
        .append(", ").append(rowNum).append(" ")
        .append(DataGridBundle.message("TablePositionWidgetHelper.row", Math.min(rowNum, 2)));

      if (editing) return CompletableFuture.completedFuture(sb.toString());
      sb.append(PositionPanel.SPACE);
    }
    if (editing) CompletableFuture.completedFuture("");

    TableGoToRowHelper.Counter counter = TableGoToRowHelper.Counter.get(myTable);
    int minRow = counter.verticalUnit(myGrid);
    int minColumn = counter.horizontalUnit(myGrid);
    if (minColumn == -1 || minRow == -1) return CompletableFuture.completedFuture(sb.toString());

    sb.append(minRow + 1)
      .append(PositionPanel.SEPARATOR)
      .append(minColumn + 1);
    return CompletableFuture.completedFuture(sb.toString());
  }
}
