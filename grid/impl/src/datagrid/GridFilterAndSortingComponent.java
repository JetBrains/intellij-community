package com.intellij.database.datagrid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface GridFilterAndSortingComponent {
  int FILTER_PREFERRED_SIZE = 300;

  @NotNull
  JComponent getComponent();

  void toggleSortingPanel(boolean state);

  @NotNull
  GridEditorPanel getFilterPanel();

  @Nullable
  GridEditorPanel getSortingPanel();
}
