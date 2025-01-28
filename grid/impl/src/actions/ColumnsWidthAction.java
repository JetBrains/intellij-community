package com.intellij.database.actions;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.actions.GridAction;
import com.intellij.database.run.ui.ResultViewWithColumns;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public abstract class ColumnsWidthAction extends DumbAwareAction implements GridAction {
  private static final int DELTA = 8;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    SelectionModel<GridRow, GridColumn> selectionModel = grid == null ? null : grid.getSelectionModel();
    int selectedColumns = selectionModel instanceof SelectionModelWithViewColumns
                          ? ((SelectionModelWithViewColumns)selectionModel).selectedViewColumnsCount() : 0;
    ResultViewWithColumns resultView = grid == null ? null : ObjectUtils.tryCast(grid.getResultView(), ResultViewWithColumns.class);
    e.getPresentation().setEnabledAndVisible(grid != null && resultView != null && !grid.isEditing() && selectedColumns > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    ResultViewWithColumns resultView =
      dataGrid == null ? null : ObjectUtils.tryCast(dataGrid.getResultView(), ResultViewWithColumns.class);
    if (resultView != null) resultView.changeSelectedColumnsWidth(getDelta());
  }

  abstract int getDelta();

  public static class DecreaseColumnsWidthAction extends ColumnsWidthAction {
    @Override
    int getDelta() {
      return -DELTA;
    }
  }

  public static class IncreaseColumnsWidthAction extends ColumnsWidthAction {
    @Override
    int getDelta() {
      return DELTA;
    }
  }
}
