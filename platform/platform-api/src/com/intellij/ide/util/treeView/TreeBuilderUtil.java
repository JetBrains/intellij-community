/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.util.treeView;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TreeBuilderUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.TreeBuilderUtil");

  public static void storePaths(AbstractTreeBuilder treeBuilder, DefaultMutableTreeNode root, List<Object> pathsToExpand, List<Object> selectionPaths, boolean storeElementsOnly) {
    if (!treeBuilder.wasRootNodeInitialized()) return;

    JTree tree = treeBuilder.getTree();
    TreePath path = new TreePath(root.getPath());
    if (tree.isPathSelected(path)){
      selectionPaths.add(storeElementsOnly ? ((NodeDescriptor)root.getUserObject()).getElement() : path);
    }
    if (tree.isExpanded(path) || root.getChildCount() == 0){
      pathsToExpand.add(storeElementsOnly ? ((NodeDescriptor)root.getUserObject()).getElement() : path);
      _storePaths(tree, root, pathsToExpand, selectionPaths, storeElementsOnly);
    }
  }

  private static void _storePaths(JTree tree, DefaultMutableTreeNode root, List<Object> pathsToExpand, List<Object> selectionPaths, boolean storeElementsOnly) {
    ArrayList childNodes = TreeUtil.childrenToArray(root);
    for (final Object childNode1 : childNodes) {
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
      TreePath path = new TreePath(childNode.getPath());
      final Object userObject = childNode.getUserObject();
      if (tree.isPathSelected(path)) {
        if (!(userObject instanceof NodeDescriptor)) {
          LOG.error("Node: " + childNode + "; userObject: " + userObject + " of class " + userObject.getClass());
        }
        selectionPaths.add(storeElementsOnly ? ((NodeDescriptor)userObject).getElement() : path);
      }
      if (tree.isExpanded(path) || childNode.getChildCount() == 0) {
        pathsToExpand.add(storeElementsOnly && userObject instanceof NodeDescriptor
                          ? ((NodeDescriptor)userObject).getElement()
                          : path);
        _storePaths(tree, childNode, pathsToExpand, selectionPaths, storeElementsOnly);
      }
    }
  }

  public static void restorePaths(AbstractTreeBuilder treeBuilder, List<Object> pathsToExpand, List<Object> selectionPaths, boolean elementsOnly) {
    JTree tree = treeBuilder.getTree();
    if (!elementsOnly){
      for (Object path : pathsToExpand) {
        tree.expandPath((TreePath)path);
      }
      tree.addSelectionPaths(selectionPaths.toArray(new TreePath[selectionPaths.size()]));
    }
    else{
      for (Object element : pathsToExpand) {
        treeBuilder.buildNodeForElement(element);
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node != null) {
          tree.expandPath(new TreePath(node.getPath()));
        }
      }
      for (Object element : selectionPaths) {
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node != null) {
          DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
          tree.addSelectionPath(new TreePath(treeModel.getPathToRoot(node)));
        }
      }
    }
  }

  public static boolean isNodeSelected(JTree tree, DefaultMutableTreeNode node){
    TreePath[] selectionPaths = tree.getSelectionPaths();
    return selectionPaths != null && selectionPaths.length != 0 &&
           ContainerUtil.find(Arrays.asList(selectionPaths), new TreePath(node.getPath())) != null;

  }


  public static boolean isNodeOrChildSelected(JTree tree, DefaultMutableTreeNode node){
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return false;

    TreePath path = new TreePath(node.getPath());
    for (TreePath selectionPath : selectionPaths) {
      if (path.isDescendant(selectionPath)) return true;
    }

    return false;
  }
}
