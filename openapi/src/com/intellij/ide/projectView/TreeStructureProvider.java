package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;

import java.util.Collection;

public interface TreeStructureProvider {

  Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings);

}
