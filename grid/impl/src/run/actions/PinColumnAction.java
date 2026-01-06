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
 * Action to pin a column to the left side of the table.
 * Pinned columns remain visible when scrolling horizontally.
 */
public class PinColumnAction extends ColumnHeaderActionBase {
  public PinColumnAction() {
    super(false);
  }

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    TableResultView table = getTableResultView(grid);
    boolean visible = table != null && columnIdxs.size() == 1;
    
    if (visible) {
      ModelIndex<GridColumn> columnIdx = columnIdxs.first();
      boolean isPinned = table.isColumnPinned(columnIdx);
      visible = !isPinned; // Only show "Pin" action if not already pinned
    }
    
    e.getPresentation().setEnabledAndVisible(visible);
    if (columnIdxs.size() == 1) {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.PinColumn.text"));
    }
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    TableResultView table = getTableResultView(grid);
    if (table != null && columnIdxs.size() == 1) {
      ModelIndex<GridColumn> columnIdx = columnIdxs.first();
      table.pinColumn(columnIdx);
    }
  }

  private TableResultView getTableResultView(@NotNull DataGrid grid) {
    if (grid.getResultView() instanceof TableResultView) {
      return (TableResultView) grid.getResultView();
    }
    return null;
  }
}
