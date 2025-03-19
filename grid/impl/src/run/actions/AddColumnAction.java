package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class AddColumnAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(GridUtil.canMutateColumns(e.getData(DatabaseDataKeys.DATA_GRID_KEY)));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (dataGrid == null) return;
    insertColumn(dataGrid);
  }

  public static void insertColumn(@NotNull DataGrid grid) {
    GridDataHookUp<?, ?> hookup = grid.getDataHookup();
    if (hookup instanceof CsvDocumentDataHookUp) {
      CsvFormat format = ((CsvDocumentDataHookUp)hookup).getFormat();
      if (format.headerRecord == null) {
        insertColumn(grid, null);
        return;
      }
    }
    NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
    JTextField nameField = contentPanel.getTextField();
    String defaultColumnName = GridUtilCore.generateColumnName(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS));
    nameField.setText(defaultColumnName);
    nameField.selectAll();
    JBPopup popup = NewItemPopupUtil.createNewItemPopup(DataGridBundle.message("action.insert.column.dialog.title"), contentPanel, nameField);
    contentPanel.setApplyAction(event -> {
      String name = nameField.getText();
      if (StringUtil.isEmptyOrSpaces(name)) return;
      popup.closeOk(event);
      insertColumn(grid, name);
    });
    popup.showCenteredInCurrentWindow(grid.getProject());
  }

  public static void insertColumn(@NotNull DataGrid grid, @Nullable String columnName) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = GridUtil.getColumnsMutator(grid);
    if (mutator == null) return;
    if (mutator.isUpdateImmediately() && mutator.hasPendingChanges()) {
      grid.submit().doWhenDone(() -> mutator.insertColumn(newInsertOrCloneColumnRequestSource(grid), columnName));
      return;
    }
    mutator.insertColumn(newInsertOrCloneColumnRequestSource(grid), columnName);
  }

  public static GridRequestSource newInsertOrCloneColumnRequestSource(@NotNull DataGrid grid) {
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid));
    source.getActionCallback().doWhenDone(() -> {
      ModelIndex<GridColumn> column = ModelIndex.forColumn(grid, grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnCount() - 1);
      GridUtil.scrollToLocally(grid, ViewIndex.forRow(grid, 0), column.toView(grid));
    });
    return source;
  }
}
