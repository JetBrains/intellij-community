package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface TreeStructureProvider {

  Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings);

  @Nullable Object getData(Collection<AbstractTreeNode> selected, String dataName);
}
