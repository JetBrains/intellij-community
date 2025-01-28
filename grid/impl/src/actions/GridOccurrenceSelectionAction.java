package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.actions.GridAction;
import com.intellij.database.run.ui.grid.selection.GridSelectionTracker;
import com.intellij.database.run.ui.grid.selection.GridSelectionTrackerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public abstract class GridOccurrenceSelectionAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(grid != null && grid.getSelectionModel().getTracker().canPerformOperation(getOperation()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null || !grid.getSelectionModel().getTracker().canPerformOperation(getOperation())) return;
    grid.getSelectionModel().getTracker().performOperation(getOperation());
  }

  abstract @NotNull GridSelectionTracker.Operation getOperation();

  public static class SelectNextOccurrenceAction extends GridOccurrenceSelectionAction {
    @Override
    @NotNull
    GridSelectionTracker.Operation getOperation() {
      return GridSelectionTrackerImpl.OperationImpl.SELECT_NEXT;
    }
  }

  public static class UnselectPreviousOccurrenceAction extends GridOccurrenceSelectionAction {
    @Override
    @NotNull
    GridSelectionTracker.Operation getOperation() {
      return GridSelectionTrackerImpl.OperationImpl.UNSELECT_PREVIOUS;
    }
  }

  public static class SelectAllOccurrencesAction extends GridOccurrenceSelectionAction {
    @Override
    @NotNull
    GridSelectionTracker.Operation getOperation() {
      return GridSelectionTrackerImpl.OperationImpl.SELECT_ALL;
    }
  }
}
