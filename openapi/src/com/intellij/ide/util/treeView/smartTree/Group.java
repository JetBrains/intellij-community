package com.intellij.ide.util.treeView.smartTree;

import com.intellij.navigation.ItemPresentation;

import java.util.Collection;

public interface Group {
  ItemPresentation getPresentation();
  Collection<TreeElement> getChildren();
}
