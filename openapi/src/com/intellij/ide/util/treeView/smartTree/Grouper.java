package com.intellij.ide.util.treeView.smartTree;

import com.intellij.ide.util.treeView.AbstractTreeNode;

import java.util.Collection;

public interface Grouper extends TreeAction{
  Collection<Group> group(final AbstractTreeNode parent, Collection<TreeElement> children);
}
