package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public class CopyColumnNameAction extends ColumnHeaderActionBase {
  public CopyColumnNameAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    JBIterable<String> names = columnIdxs.asIterable()
      .map(idx -> grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(idx))
      .filter(column -> column != null)
      .map(column -> column.getName());
    if (names.isEmpty()) return;
    UIUtil.invokeLaterIfNeeded(() -> CopyPasteManager.getInstance().setContents(new StringSelection(Strings.join(names, ","))));
  }
}