package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.find.SearchSession;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

public class FindInGridAction extends ToggleAction implements DumbAware, GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    e.getPresentation().setEnabled(grid != null && grid.getResultView().supportsCustomSearchSession());
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    SearchSession session = grid != null ? grid.getSearchSession() : null;
    return session != null;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return;
    SearchSession session = grid.getSearchSession();
    if (state) {
      if (session == null) {
        session = grid.getResultView().createSearchSession(null, grid.getPanel().getSecondTopComponent());
      }
      if (session == null) return;
      session.getComponent().requestFocusInTheSearchFieldAndSelectContent(e.getProject());
    }
    else if (session != null) {
      if (e.getInputEvent() instanceof KeyEvent) { // don't close on ctrl+F
        GridSearchSession<?, ?> gridSession = ObjectUtils.tryCast(session, GridSearchSession.class);
        session = grid.getResultView().createSearchSession(session.getFindModel(), gridSession == null ? null : gridSession.getPreviousFilterComponent());
        if (session == null) return;
        session.getComponent().requestFocusInTheSearchFieldAndSelectContent(e.getProject());
      } else {
        session.close();
      }
    }
  }
}
