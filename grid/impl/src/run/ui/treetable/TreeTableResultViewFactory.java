package com.intellij.database.run.ui.treetable;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.run.ui.ResultViewFactory;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Liudmila Kornilova
 **/
public class TreeTableResultViewFactory implements ResultViewFactory {
  public static final TreeTableResultViewFactory TREE_TABLE_FACTORY = new TreeTableResultViewFactory();

  @Override
  public @NotNull ResultView createResultView(@NotNull DataGrid resultPanel, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions) {
    return new TreeTableResultView(resultPanel);
  }

  @Override
  public @NotNull JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    return resultView.getComponent();
  }
}
