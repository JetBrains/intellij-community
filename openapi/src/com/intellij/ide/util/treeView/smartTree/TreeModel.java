package com.intellij.ide.util.treeView.smartTree;

public interface TreeModel {
  TreeElement getRoot();
  Grouper[] getGroupers();
  Sorter[] getSorters();
  Filter[] getFilters();
}
