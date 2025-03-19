package com.intellij.database.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.EditMaximizedView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.run.ui.EditMaximizedViewKt.findEditMaximized;

/**
 * @author Liudmila Kornilova
 **/
public class HideEditMaximizedAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(e.isFromActionToolbar()
                                ? DataGridBundle.message("action.Console.TableResult.HideEditMaximized.short.text")
                                : DataGridBundle.message("action.Console.TableResult.HideEditMaximized.text"));

    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    boolean visible = findEditMaximized(e.getDataContext()) != null;
    e.getPresentation().setEnabledAndVisible(visible);
  }


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    EditMaximizedView view = findEditMaximized(e.getDataContext());
    if (grid == null || view == null) return;
    hideValueEditor(grid, view);
  }

  public static void hideValueEditor(@NotNull DataGrid grid, @NotNull EditMaximizedView view) {
    grid.getPanel().removeSideView(view);
  }
}
