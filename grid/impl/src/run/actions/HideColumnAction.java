package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.TableResultPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class HideColumnAction extends ColumnHeaderActionBase {
  public HideColumnAction() {
    super(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    super.update(e, grid, columnIdxs);
    if (e.getPresentation().isVisible() && anyPinned(grid, columnIdxs)) {
      e.getPresentation().setEnabledAndVisible(false); // a pinned column is unpinned via Unpin, not hidden
      return;
    }
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

  private static boolean anyPinned(@NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    TableResultPanel panel = ObjectUtils.tryCast(grid, TableResultPanel.class);
    if (panel == null || !TableResultPanel.isColumnPinningEnabled()) return false;
    for (ModelIndex<GridColumn> index : columnIdxs.asIterable()) {
      if (panel.isColumnPinned(index)) return true;
    }
    return false;
  }
}
