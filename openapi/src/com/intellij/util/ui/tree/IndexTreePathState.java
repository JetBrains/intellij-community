package com.intellij.util.ui.tree;

import javax.swing.tree.TreePath;
import javax.swing.tree.TreeNode;
import javax.swing.*;

public class IndexTreePathState implements TreePathState {
  private TreePath mySelectionPath;
  private int[] myIndicies;

  public IndexTreePathState(TreePath path) {
    mySelectionPath = path;
    myIndicies = pathToChildIndecies(path);
  }

  public TreePath getRestoredPath() {
    int aliveIndex = findLowestAliveNodeIndex(mySelectionPath);
    if (aliveIndex == mySelectionPath.getPathCount() - 1) return mySelectionPath;
    TreeNode aliveAncestor = (TreeNode) mySelectionPath.getPathComponent(aliveIndex);
    TreePath newSelection = TreeUtil.getPathFromRoot(aliveAncestor);
    int childrenLeft = aliveAncestor.getChildCount();
    if (childrenLeft != 0) {
      int newSelectedChildIndex = Math.min(myIndicies[aliveIndex + 1], childrenLeft - 1);
      newSelection = newSelection.pathByAddingChild(aliveAncestor.getChildAt(newSelectedChildIndex));
    }
    return newSelection;
  }

  public void restoreSelection(JTree tree) {
    TreeUtil.selectPath(tree, getRestoredPath());
  }

  private static int findLowestAliveNodeIndex(TreePath path) {
    Object[] nodes = path.getPath();
    for (int i = 1; i < nodes.length; i++) {
      TreeNode node = (TreeNode) nodes[i];
      if (node.getParent() == null) return i - 1;
    }
    return nodes.length - 1;
  }

  private static int[] pathToChildIndecies(TreePath path) {
    int[] result = new int[path.getPathCount()];
    for (int i = 0; i < path.getPathCount(); i++) {
      TreeNode node = (TreeNode) path.getPathComponent(i);
      TreeNode parent = node.getParent();
      result[i] = parent != null ? parent.getIndex(node) : 0;
    }
    return result;
  }
}
