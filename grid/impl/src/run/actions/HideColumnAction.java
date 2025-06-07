package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class HideColumnAction extends ColumnHeaderActionBase {
  public HideColumnAction() {
    super(true);
  }

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    super.update(e, grid, columnIdxs);
    if (columnIdxs.size() == 1) {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.HideColumn.text"));
      return;
    }
    e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.HideColumns.text"));
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    for (ModelIndex<GridColumn> index : columnIdxs.asIterable()) {
      grid.setColumnEnabled(index, false);
    }
  }
}
