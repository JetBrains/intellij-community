package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RenameColumnAction extends SingleColumnHeaderAction {
  public RenameColumnAction() {
    super(true);
  }

  @Override
  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    GridDataHookUp<?, ?> hookUp = grid.getDataHookup();
    e.getPresentation().setEnabledAndVisible(columnIdxs.size() == 1 && columnIdxs.first().isValid(grid) &&
                                             (grid.getDataSupport().isInsertedColumn(columnIdxs.first()) ||
                                              hookUp instanceof CsvDocumentDataHookUp));
  }


  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> columnIdx) {
    NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
    JTextField nameField = contentPanel.getTextField();
    GridColumn column = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null) return;
    String name = column.getName();
    nameField.setText(name);
    nameField.selectAll();
    JBPopup popup =
      NewItemPopupUtil.createNewItemPopup(DataGridBundle.message("action.Console.TableResult.RenameColumn.text"), contentPanel, nameField);
    contentPanel.setApplyAction(event -> {
      String newName = nameField.getText();
      if (StringUtil.isEmptyOrSpaces(newName)) return;
      popup.closeOk(event);
      renameColumn(grid, columnIdx, newName);
    });
    popup.showCenteredInCurrentWindow(grid.getProject());
  }

  private static void renameColumn(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> idx, @NotNull String name) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = GridUtil.getColumnsMutator(grid);
    if (mutator == null) return;
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid));
    if (mutator.isUpdateImmediately() && mutator.hasPendingChanges()) {
      grid.submit().doWhenDone(() -> mutator.renameColumn(source, idx, name));
      return;
    }
    mutator.renameColumn(source, idx, name);
  }
}