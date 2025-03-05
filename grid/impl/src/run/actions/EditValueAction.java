package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class EditValueAction extends AnAction implements DumbAware, GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public EditValueAction() {
    super();

    // we want this action available in settings and csv format preview modal dialogs
    setEnabledInModalContext(true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    String text = getText(!dataGrid.isEditable());
    String description = getDescription(!dataGrid.isEditable());
    boolean visible = dataGrid.isEditable() && e.isFromContextMenu();

    e.getPresentation().setText(text);
    e.getPresentation().setDescription(description);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(isEnabled(dataGrid, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)));
  }

  boolean isEnabled(@NotNull DataGrid grid, Component contextComponent) {
    return contextComponent != null && DataGridUIUtil.isInsideGrid(grid, contextComponent) &&
           grid.isCellEditingAllowed() && grid.isReady() && !grid.isEditing() && isSelectedColumnsEditable(grid);
  }

  private static boolean isSelectedColumnsEditable(@NotNull DataGrid grid) {
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATABASE_DATA);
    List<GridColumn> columns = model.getColumns(grid.getSelectionModel().getSelectedColumns());
    return JBIterable.from(columns).filter(GridUtilCore::isRowId).isEmpty();
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid != null) {
      dataGrid.editSelectedCell();
    }
  }

  protected @NlsActions.ActionText String getText(boolean viewMode) {
    return DataGridBundle.message("action.Console.TableResult.EditValue.adjusted.text", viewMode ? 0 : 1);
  }

  protected @NlsActions.ActionDescription String getDescription(boolean viewMode) {
    return DataGridBundle.message("action.Console.TableResult.EditValue.edit.selected.cell", viewMode ? 0 : 1);
  }
}
