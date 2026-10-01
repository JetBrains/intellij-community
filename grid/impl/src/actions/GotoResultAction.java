package com.intellij.database.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.connection.throwable.info.SimpleErrorInfo;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.actions.GridAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Point;
import java.util.function.Predicate;

public abstract class GotoResultAction extends DumbAwareAction implements GridAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    boolean hasResults = e.getData(DatabaseDataKeys.DATA_GRID_RESULT_CONTENTS_KEY) != null;
    e.getPresentation().setEnabledAndVisible(grid != null && hasResults && isEnabled(grid, e));
  }

  protected abstract boolean isEnabled(@NotNull DataGrid grid, @NotNull AnActionEvent e);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) return;
    Content[] results = e.getData(DatabaseDataKeys.DATA_GRID_RESULT_CONTENTS_KEY);
    if (results == null) return;
    Predicate<Content> resultPredicate = getResultPredicate(grid, e);
    if (resultPredicate == null) return;
    for (Content content : results) {
      if (resultPredicate.test(content)) {
        ContentManager manager = content.isValid() ? content.getManager() : null;
        if (manager != null) manager.setSelectedContent(content, false, false);
        return;
      }
    }
    Component component = grid.getPanel().getComponent();
    ApplicationManager.getApplication().invokeLater(
      () -> {
        if (!component.isValid()) return;
        GridUtil.showErrorBalloon(SimpleErrorInfo.create(getErrorMessage()), component, getErrorPosition(component));
      }
    );
  }

  protected abstract @Nullable Predicate<Content> getResultPredicate(@NotNull DataGrid grid, @NotNull AnActionEvent e);

  protected abstract @Nls @NotNull String getErrorMessage();

  protected static @NotNull Point getErrorPosition(@NotNull Component component) {
    Point mousePosition = component.getMousePosition();
    return mousePosition != null ? mousePosition : new Point(0, 0);
  }
}
