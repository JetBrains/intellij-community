package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.FloatingPagingManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.Frontend;

import static com.intellij.database.datagrid.GridUtil.hidePageActions;

/**
* @author Gregory.Shrago
*/
public abstract class PageAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected abstract boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel);

  protected abstract void actionPerformed(GridRequestSource source,
                                          GridLoader loader);

  @Override
  public void update(final @NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null || dataGrid.getDataHookup() instanceof DocumentDataHookUp) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean enabled = dataGrid.isReady() && isEnabled(dataGrid.getDataHookup().getPageModel());
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(isVisible(e, dataGrid) && (enabled || !ActionPlaces.EDITOR_POPUP.equals(e.getPlace())));
  }

  protected boolean isVisible(@NotNull AnActionEvent e, DataGrid dataGrid) {
    return !hidePageActions(dataGrid, e.getPlace());
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) return;
    if (!dataGrid.stopEditing()) {
      dataGrid.cancelEditing();
    }
    GridMutator<GridRow, GridColumn> mutator = dataGrid.getDataHookup().getMutator();
    if (mutator == null || !mutator.hasPendingChanges() || GridUtil.showIgnoreUnsubmittedChangesYesNoDialog(dataGrid)) {
      actionPerformed(new GridRequestSource(new DataGridRequestPlace(dataGrid)), dataGrid.getDataHookup().getLoader());
    }
  }

  public static class Reload extends PageAction {

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return true;
    }

    @Override
    protected boolean isVisible(@NotNull AnActionEvent e, DataGrid dataGrid) {
      return super.isVisible(e, dataGrid) && !(dataGrid.getDataHookup() instanceof ImmutableDataHookUp);
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.reloadCurrentPage(source);
    }
  }
  
  public static abstract class NavigationAction extends PageAction implements Frontend {
    
    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      additionalUpdateVisibilityCheck(e);
      FloatingPagingManager.adjustAction(e);
    }

    private static void additionalUpdateVisibilityCheck(@NotNull AnActionEvent e) {
      DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
      if (dataGrid == null) return;
      GridPagingModel<GridRow, GridColumn> pageModel = dataGrid.getDataHookup().getPageModel();

      final int pageSize = pageModel.getPageSize();
      if (pageSize == GridPagingModel.UNLIMITED_PAGE_SIZE) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }

      final long totalRowCount = pageModel.getTotalRowCount();
      if ((long)pageSize >= totalRowCount) {
        e.getPresentation().setEnabledAndVisible(false);
      }
    }
  }

  public static class First extends NavigationAction {

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isFirstPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadFirstPage(source);
    }
  }

  public static class Last extends NavigationAction {

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isLastPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadLastPage(source);
    }
  }

  public static class Next extends NavigationAction {

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isLastPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadNextPage(source);
    }
  }

  public static class Previous extends NavigationAction {

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isFirstPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadPreviousPage(source);
    }
  }
}