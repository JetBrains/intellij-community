package com.intellij.ide.util.treeView.smartTree;

public interface Filter extends TreeAction{
  boolean isVisible(TreeElement treeNode);
}
