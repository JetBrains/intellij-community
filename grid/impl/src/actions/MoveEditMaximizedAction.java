package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridPanel.ViewPosition;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.actions.ShowEditMaximizedAction.savePosition;
import static com.intellij.database.run.ui.EditMaximizedViewKt.findEditMaximized;

/**
 * @author Liudmila Kornilova
 **/
public abstract class MoveEditMaximizedAction extends DumbAwareAction {
  private final ViewPosition myPosition;

  MoveEditMaximizedAction(@NotNull ViewPosition position) {
    myPosition = position;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    EditMaximizedView view = findEditMaximized(e.getDataContext());
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (view == null || grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(grid.getPanel().getSideView(myPosition) == null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    EditMaximizedView view = findEditMaximized(e.getDataContext());
    if (grid == null || view == null) return;
    savePosition(myPosition);
    var panel = grid.getPanel();
    panel.putSideView(view, myPosition, panel.locateSideView(view));
  }

  public static class MoveEditMaximizedToRightAction extends MoveEditMaximizedAction {
    MoveEditMaximizedToRightAction() {
      super(ViewPosition.RIGHT);
    }
  }

  public static class MoveEditMaximizedToBottomAction extends MoveEditMaximizedAction {
    MoveEditMaximizedToBottomAction() {
      super(ViewPosition.BOTTOM);
    }
  }
}
