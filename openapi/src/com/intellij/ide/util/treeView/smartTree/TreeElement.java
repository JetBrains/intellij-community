package com.intellij.ide.util.treeView.smartTree;

import com.intellij.navigation.ItemPresentation;

public interface TreeElement {
  ItemPresentation getPresentation();
  TreeElement[] getChildren();
}
