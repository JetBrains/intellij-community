package com.intellij.ide.util.treeView.smartTree;

import org.jetbrains.annotations.NotNull;

public interface TreeModel {
  @NotNull TreeElement getRoot();
  @NotNull Grouper[] getGroupers();
  @NotNull Sorter[] getSorters();
  @NotNull Filter[] getFilters();
}
