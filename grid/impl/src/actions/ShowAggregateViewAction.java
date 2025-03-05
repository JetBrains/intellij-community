package com.intellij.database.actions;


import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.AggregatesTabInfoProvider;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.database.run.ui.EditMaximizedViewKt.findEditMaximized;

public class ShowAggregateViewAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private DataGrid myGrid = null;

  public void setGrid(@NotNull DataGrid grid) {
    myGrid = grid;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) grid = myGrid;
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    if (ActionPlaces.STATUS_BAR_PLACE.equals(e.getPlace())) {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.AggregateView.Widget.text"));
    }
    else {
      e.getPresentation().setText(DataGridBundle.message("action.Console.TableResult.AggregateView.text"));
    }
    Component contextComponent = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    boolean forceShow = contextComponent == grid.getResultView().getComponent() || ActionPlaces.STATUS_BAR_PLACE.equals(e.getPlace());
    boolean visible = findEditMaximized(e.getDataContext()) != null;
    e.getPresentation().setEnabledAndVisible(forceShow || !visible);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) grid = myGrid;
    if (grid == null) return;
    EditMaximizedView view = ShowEditMaximizedAction.getView(grid, e);
    view.open(tabInfoProvider -> tabInfoProvider instanceof AggregatesTabInfoProvider);

    if (grid.isEditable()) {
      JComponent focusComponent = view.getPreferedFocusComponent();
      if (focusComponent != null) focusComponent.requestFocus();
    }
  }
}
