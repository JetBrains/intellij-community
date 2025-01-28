package com.intellij.database.run.ui.text;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.run.ui.ResultViewFactory;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Liudmila Kornilova
 **/
public class TextResultViewFactory implements ResultViewFactory {
  public static final TextResultViewFactory TEXT_FACTORY = new TextResultViewFactory();

  @Override
  public @NotNull ResultView createResultView(@NotNull DataGrid resultPanel, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions) {
    return new TextResultView(resultPanel);
  }

  @Override
  public @NotNull JComponent wrap(@NotNull DataGrid grid, @NotNull ResultView resultView) {
    return resultView.getComponent();
  }
}
