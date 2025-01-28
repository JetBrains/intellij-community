package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;

import static com.intellij.database.run.actions.NotebookGridPatcherKt.RESULTS_PATCHER;


/**
 * @author Gregory.Shrago
 */
public class AddRowAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    boolean visible = grid != null && (grid.isEditable() || GridHelper.get(grid).hasTargetForEditing(grid)); // DBE-12001
    boolean enabled = visible && canAddRow(grid);

    if (enabled && grid.isEditing()) {
      enabled = !(e.getInputEvent() instanceof KeyEvent);
    }

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(visible);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) return;
    ActionCallback callback = GridUtil.addRow(dataGrid);
    GridUtil.focusDataGrid(dataGrid);
    NotebookGridPatcher patcher = dataGrid.getUserData(RESULTS_PATCHER);
    if (patcher != null && callback != null) {
      callback.doWhenDone(() -> {
        patcher.updateHeight();
      });
    }
  }

  public static boolean canAddRow(@Nullable DataGrid grid) {
    boolean canAddRow = grid != null &&
                        grid.isEditable() &&
                        grid.isReady() &&
                        grid.getDataSupport().hasRowMutator() &&
                        grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount() != 0;

    if (!canAddRow) return false;

    return GridHelper.get(grid).canAddRow(grid);
  }
}
