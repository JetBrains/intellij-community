package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.table.TableResultView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action to unpin a column from the left side of the table.
 */
public class UnpinColumnAction extends ColumnHeaderActionBase {
  public UnpinColumnAction() {
    super(false);
  }

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    TableResultView table = getTableResultView(grid);
    boolean visible = table != null && columnIdxs.size() == 1;
    
    if (visible) {
      ModelIndex<GridColumn> columnIdx = columnIdxs.first();
      boolean isPinned = table.isColumnPinned(columnIdx);
      visible = isPinned; // Only show "Unpin" action if already pinned
    }
    
    e.getPresentation().setEnabledAndVisible(visible);
    if (columnIdxs.size() == 1) {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.UnpinColumn.text"));
    }
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    TableResultView table = getTableResultView(grid);
    if (table != null && columnIdxs.size() == 1) {
      ModelIndex<GridColumn> columnIdx = columnIdxs.first();
      table.unpinColumn(columnIdx);
    }
  }

  private TableResultView getTableResultView(@NotNull DataGrid grid) {
    if (grid.getResultView() instanceof TableResultView) {
      return (TableResultView) grid.getResultView();
    }
    return null;
  }
}
