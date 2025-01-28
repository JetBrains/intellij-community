package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridPresentationMode;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.GridPresentationMode.*;

public abstract class ViewAsAction extends ToggleAction implements DumbAware, GridAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public ViewAsAction() {
    // we want this action available in settings and csv format preview modal dialogs
    setEnabledInModalContext(true);
  }

  abstract @NotNull GridPresentationMode getPresentationMode(@NotNull DataGrid dataGrid);

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(dataGrid != null);
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    return dataGrid != null && dataGrid.getPresentationMode() == getPresentationMode(dataGrid);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    if (dataGrid != null) dataGrid.setPresentationMode(getPresentationMode(dataGrid));
  }

  public static class ViewAsTableAction extends ViewAsAction {
    @Override
    @NotNull GridPresentationMode getPresentationMode(@NotNull DataGrid dataGrid) {
      return TABLE;
    }
  }

  public static class ViewAsTreeTableAction extends ViewAsAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      if (dataGrid != null && isHierarchical(dataGrid)) {
        Toggleable.setSelected(e.getPresentation(), false);
        e.getPresentation().setEnabled(false);
      }
      else {
        super.update(e);
      }
    }

    @Override
    @NotNull GridPresentationMode getPresentationMode(@NotNull DataGrid dataGrid) {
      return TREE_TABLE;
    }
  }

  public static class ViewAsExtractorAction extends ViewAsAction {
    @Override
    @NotNull GridPresentationMode getPresentationMode(@NotNull DataGrid dataGrid) {
      return TEXT;
    }
  }

  public static class TransposeViewAction extends ToggleAction implements DumbAware {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      if (dataGrid != null && (dataGrid.getPresentationMode() == TREE_TABLE || isHierarchical(dataGrid))) {
        Toggleable.setSelected(e.getPresentation(), false);
        e.getPresentation().setEnabled(false);
      }
      else {
        super.update(e);
      }
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      return dataGrid != null && dataGrid.getResultView().isTransposed();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
      if (dataGrid != null) dataGrid.getResultView().setTransposed(state);
    }
  }

  private static boolean isHierarchical(@NotNull DataGrid grid) {
    return grid.getDataModel(DataAccessType.DATABASE_DATA) instanceof HierarchicalColumnsDataGridModel;
  }
}
