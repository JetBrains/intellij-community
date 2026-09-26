// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.util.treeView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.List;

@Deprecated(forRemoval = true) // TODO move the code to other util class if needed
public final class TreeBuilderUtil {
  private static final Logger LOG = Logger.getInstance(TreeBuilderUtil.class);

  /**
   * @see TreeBuilderUtil#_storePaths(JTree, DefaultMutableTreeNode, List, List, boolean, boolean)
   */
  public static void storePaths(@NotNull JTree tree, @NotNull DefaultMutableTreeNode root, @NotNull List<Object> pathsToExpand, @NotNull List<Object> selectionPaths, boolean storeElementsOnly) {
    storePaths(tree, root, pathsToExpand, selectionPaths, storeElementsOnly, true);
  }

  /**
   * Saves paths that are currently expanded and selected in the tree.
   * @param tree The JTree instance.
   * @param root The root node of the tree.
   * @param pathsToExpand List to store paths that are expanded.
   * @param selectionPaths List to store paths that are selected.
   * @param storeElementsOnly If true, only store the elements associated with the paths, otherwise store the paths themselves.
   * @param expandLeaves If true, consider leaf nodes as expanded.
   */
  public static void storePaths(@NotNull JTree tree, @NotNull DefaultMutableTreeNode root, @NotNull List<Object> pathsToExpand, @NotNull List<Object> selectionPaths, boolean storeElementsOnly, boolean expandLeaves) {
    TreePath path = new TreePath(root.getPath());
    if (tree.isPathSelected(path)){
      selectionPaths.add(storeElementsOnly ? ((NodeDescriptor<?>)root.getUserObject()).getElement() : path);
    }
    if (tree.isExpanded(path) || (expandLeaves && root.getChildCount() == 0)){
      pathsToExpand.add(storeElementsOnly ? ((NodeDescriptor<?>)root.getUserObject()).getElement() : path);
      _storePaths(tree, root, pathsToExpand, selectionPaths, storeElementsOnly, expandLeaves);
    }
  }

  private static void _storePaths(@NotNull JTree tree, @NotNull DefaultMutableTreeNode root, @NotNull List<Object> pathsToExpand, @NotNull List<Object> selectionPaths, boolean storeElementsOnly, boolean expandLeaves) {
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
      if (tree.isExpanded(path) || (expandLeaves && childNode.getChildCount() == 0)) {
        pathsToExpand.add(storeElementsOnly && userObject instanceof NodeDescriptor
                          ? ((NodeDescriptor<?>)userObject).getElement()
                          : path);
        _storePaths(tree, childNode, pathsToExpand, selectionPaths, storeElementsOnly, expandLeaves);
      }
    }
  }
}
