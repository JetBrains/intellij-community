package com.intellij.database.run.ui;

import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ResultViewColumn;
import org.jetbrains.annotations.NotNull;

/**
 * @author Liudmila Kornilova
 **/
public interface ResultViewWithColumns {
  void changeSelectedColumnsWidth(int delta);
  void createDefaultColumnsFromModel();
  ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column);
}
