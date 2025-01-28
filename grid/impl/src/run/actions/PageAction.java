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

  protected static void additionalUpdateVisibilityCheck(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) return;
    GridPagingModel<GridRow, GridColumn> pageModel = dataGrid.getDataHookup().getPageModel();

    if (pageModel.getPageSize() == GridPagingModel.UNLIMITED_PAGE_SIZE || (long)pageModel.getPageSize() >= pageModel.getTotalRowCount()) {
      e.getPresentation().setEnabledAndVisible(false);
    }
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

  public static class First extends PageAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      additionalUpdateVisibilityCheck(e);
      FloatingPagingManager.adjustAction(e);
    }

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isFirstPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadFirstPage(source);
    }
  }

  public static class Last extends PageAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      additionalUpdateVisibilityCheck(e);
      FloatingPagingManager.adjustAction(e);
    }

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isLastPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadLastPage(source);
    }
  }

  public static class Next extends PageAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      additionalUpdateVisibilityCheck(e);
      FloatingPagingManager.adjustAction(e);
    }

    @Override
    protected boolean isEnabled(GridPagingModel<GridRow, GridColumn> pageModel) {
      return !pageModel.isLastPage();
    }

    @Override
    protected void actionPerformed(GridRequestSource source, GridLoader loader) {
      loader.loadNextPage(source);
    }
  }

  public static class Previous extends PageAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      additionalUpdateVisibilityCheck(e);
      FloatingPagingManager.adjustAction(e);
    }

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
