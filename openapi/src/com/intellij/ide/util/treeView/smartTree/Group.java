package com.intellij.ide.util.treeView.smartTree;

import com.intellij.navigation.ItemPresentation;

public interface Group {
  ItemPresentation getPresentation();
  boolean contains(TreeElement object);
}
