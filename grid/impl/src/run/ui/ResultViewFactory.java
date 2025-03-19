package com.intellij.database.run.ui;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridPresentationMode;
import com.intellij.database.datagrid.ResultView;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.database.run.ui.table.TableResultViewFactory.TABLE_FACTORY;
import static com.intellij.database.run.ui.text.TextResultViewFactory.TEXT_FACTORY;
import static com.intellij.database.run.ui.treetable.TreeTableResultViewFactory.TREE_TABLE_FACTORY;

/**
 * @author Liudmila Kornilova
 **/
public interface ResultViewFactory {
  @NotNull
  ResultView createResultView(@NotNull DataGrid resultPanel, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions);

  @NotNull
  JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView);

  static ResultViewFactory of(@NotNull GridPresentationMode mode) {
    return switch (mode) {
      case TABLE -> TABLE_FACTORY;
      case TREE_TABLE -> TREE_TABLE_FACTORY;
      case TEXT -> TEXT_FACTORY;
    };
  }
}
