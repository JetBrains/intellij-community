// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.List;

public final class TreeBuilderUtil {
  private static final Logger LOG = Logger.getInstance(TreeBuilderUtil.class);

  public static void storePaths(@NotNull JTree tree, @NotNull DefaultMutableTreeNode root, @NotNull List<Object> pathsToExpand, @NotNull List<Object> selectionPaths, boolean storeElementsOnly) {
    TreePath path = new TreePath(root.getPath());
    if (tree.isPathSelected(path)){
      selectionPaths.add(storeElementsOnly ? ((NodeDescriptor<?>)root.getUserObject()).getElement() : path);
    }
    if (tree.isExpanded(path) || root.getChildCount() == 0){
      pathsToExpand.add(storeElementsOnly ? ((NodeDescriptor<?>)root.getUserObject()).getElement() : path);
      _storePaths(tree, root, pathsToExpand, selectionPaths, storeElementsOnly);
    }
  }

  private static void _storePaths(@NotNull JTree tree, @NotNull DefaultMutableTreeNode root, @NotNull List<Object> pathsToExpand, @NotNull List<Object> selectionPaths, boolean storeElementsOnly) {
    List<TreeNode> childNodes = TreeUtil.listChildren(root);
    for (final Object childNode1 : childNodes) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
      TreePath path = new TreePath(childNode.getPath());
      final Object userObject = childNode.getUserObject();
      if (tree.isPathSelected(path)) {
        if (!(userObject instanceof NodeDescriptor)) {
          LOG.error("Node: " + childNode + "; userObject: " + userObject + " of class " + userObject.getClass());
          return;
        }
        selectionPaths.add(storeElementsOnly ? ((NodeDescriptor<?>)userObject).getElement() : path);
      }
      if (tree.isExpanded(path) || childNode.getChildCount() == 0) {
        pathsToExpand.add(storeElementsOnly && userObject instanceof NodeDescriptor
                          ? ((NodeDescriptor<?>)userObject).getElement()
                          : path);
        _storePaths(tree, childNode, pathsToExpand, selectionPaths, storeElementsOnly);
      }
    }
  }

  static boolean isNodeOrChildSelected(@NotNull JTree tree, @NotNull DefaultMutableTreeNode node){
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return false;

    TreePath path = new TreePath(node.getPath());
    for (TreePath selectionPath : selectionPaths) {
      if (path.isDescendant(selectionPath)) return true;
    }

    return false;
  }
}
