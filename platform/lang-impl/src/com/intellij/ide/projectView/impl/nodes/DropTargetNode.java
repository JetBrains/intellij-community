package com.intellij.ide.projectView.impl.nodes;

import javax.swing.tree.TreeNode;

/**
 * @author yole
 */
public interface DropTargetNode {
  boolean canDrop(TreeNode[] sourceNodes);

  void drop(TreeNode[] sourceNodes);
}
