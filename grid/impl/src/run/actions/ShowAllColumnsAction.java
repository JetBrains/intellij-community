package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

public final class ShowAllColumnsAction extends ColumnHeaderActionBase {
  public ShowAllColumnsAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    for (ModelIndex<GridColumn> index : columnIdxs.asIterable()) {
      grid.setColumnEnabled(index, true);
    }
  }

  @Override
  protected @NotNull ModelIndexSet<GridColumn> getColumns(@NotNull DataGrid grid) {
    JBIterable<ModelIndex<GridColumn>> disabledColumns = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnIndices().asIterable()
      .filter(idx -> {
        return !grid.isColumnEnabled(idx);
      });
    return ModelIndexSet.forColumns(grid, disabledColumns);
  }
}