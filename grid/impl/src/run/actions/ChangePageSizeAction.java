package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.GridPagingModel.UNLIMITED_PAGE_SIZE;
import static com.intellij.database.run.actions.ChangePageSizeActionGroup.format;

public class ChangePageSizeAction extends DumbAwareAction {
  private final int myPageSize;

  public ChangePageSizeAction(int pageSize) {
    super(pageSize == UNLIMITED_PAGE_SIZE ? DataGridBundle.message("action.ChangePageSize.text.all") : format(pageSize),
          pageSize == UNLIMITED_PAGE_SIZE ? DataGridBundle.message("action.ChangePageSize.description.all")
                                          : DataGridBundle.message("action.ChangePageSize.description.some", format(pageSize)),
          null);

    myPageSize = pageSize;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    e.getPresentation().setEnabledAndVisible(grid != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return;
    setPageSizeAndReload(myPageSize, grid);
  }

  public static void setPageSizeAndReload(int pageSize, @NotNull DataGrid grid) {
    GridPagingModel<GridRow, GridColumn> pageModel = grid.getDataHookup().getPageModel();
    pageModel.setPageSize(pageSize);

    GridLoader loader = grid.getDataHookup().getLoader();
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid));
    if (GridUtilCore.isPageSizeUnlimited(pageSize)) loader.load(source, 0);
    else loader.reloadCurrentPage(source);
  }
}
