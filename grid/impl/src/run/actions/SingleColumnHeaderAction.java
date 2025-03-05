package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public abstract class SingleColumnHeaderAction extends ColumnHeaderActionBase {

  public SingleColumnHeaderAction(boolean invokeOnGrid) {
    super(invokeOnGrid);
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    actionPerformed(e, grid, columnIdxs.first());
  }

  protected abstract void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> columnIdx);

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    e.getPresentation().setEnabledAndVisible(columnIdxs.size() == 1 && columnIdxs.first().isValid(grid));
  }
}
