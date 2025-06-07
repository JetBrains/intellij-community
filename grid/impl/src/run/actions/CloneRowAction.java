package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.GridCopyProvider.ChangeLimitHyperlinkListener;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.GridUtil.newInsertOrCloneRowRequestSource;

public class CloneRowAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    boolean available = dataGrid != null && dataGrid.getSelectionModel().getSelectedRowCount() == 1 &&
                        AddRowAction.canAddRow(dataGrid) && !dataGrid.isEditing();
    e.getPresentation().setEnabledAndVisible(available);
  }

  private static boolean hasTruncatedData(GridRow row) {
    for (int i = 0; i < row.getSize(); i++) {
      Object value = row.getValue(i);
      if (value instanceof LobInfo && ((LobInfo<?>)value).isTruncated()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    ModelIndex<GridRow> selectedRow = dataGrid != null ? dataGrid.getSelectionModel().getSelectedRow() : null;
    if (selectedRow != null && selectedRow.isValid(dataGrid)) {
      GridRow row = dataGrid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRow(selectedRow);
      if (row != null && hasTruncatedData(row)) {
        DataGridSettings settings = GridUtil.getSettings(dataGrid);
        String message = DataGridBundle.message("Console.TableResult.cannotCloneRow", settings == null ? 0 : 1);
        Pair<RelativePoint, Balloon.Position> position = GridUtil.getBestPositionForBalloon(dataGrid);
        JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(message, MessageType.WARNING,
                                        settings == null ? null : new ChangeLimitHyperlinkListener(dataGrid, settings))
          .createBalloon()
          .show(position.first, position.second);
      }
      else {
        cloneRow(dataGrid, selectedRow);
      }
    }
  }

  private static void cloneRow(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> rowToClone) {
    final GridMutator.RowsMutator<GridRow, GridColumn> mutator = GridUtil.getRowsMutator(grid);
    if (mutator == null) return;
    if (mutator.isUpdateImmediately() && mutator.hasPendingChanges()) {
      grid.submit().doWhenDone(() -> mutator.cloneRow(newInsertOrCloneRowRequestSource(grid), rowToClone));
      return;
    }
    mutator.cloneRow(newInsertOrCloneRowRequestSource(grid), rowToClone);
  }
}
