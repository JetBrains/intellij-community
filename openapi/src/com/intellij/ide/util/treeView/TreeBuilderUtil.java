package com.intellij.ide.util.treeView;

import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.tree.*;
import java.util.ArrayList;
import java.util.Iterator;

public class TreeBuilderUtil {
  public static void storePaths(AbstractTreeBuilder treeBuilder, DefaultMutableTreeNode root, ArrayList pathsToExpand, ArrayList selectionPaths, boolean storeElementsOnly) {
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

  private static void _storePaths(JTree tree, DefaultMutableTreeNode root, ArrayList pathsToExpand, ArrayList selectionPaths, boolean storeElementsOnly) {
    ArrayList childNodes = TreeUtil.childrenToArray(root);
    for(Iterator iterator = childNodes.iterator(); iterator.hasNext();){
      DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)iterator.next();
      TreePath path = new TreePath(childNode.getPath());
      if (tree.isPathSelected(path)){
        selectionPaths.add(storeElementsOnly ? ((NodeDescriptor)childNode.getUserObject()).getElement() : path);
      }
      if (tree.isExpanded(path) || childNode.getChildCount() == 0){
        pathsToExpand.add(storeElementsOnly && childNode.getUserObject() instanceof NodeDescriptor ? ((NodeDescriptor)childNode.getUserObject()).getElement() : path);
        _storePaths(tree, childNode, pathsToExpand, selectionPaths, storeElementsOnly);
      }
    }
  }

  public static void restorePaths(AbstractTreeBuilder treeBuilder, ArrayList pathsToExpand, ArrayList selectionPaths, boolean elementsOnly) {
    JTree tree = treeBuilder.getTree();
    if (!elementsOnly){
      for(int i = 0; i < pathsToExpand.size(); i++){
        TreePath path = (TreePath)pathsToExpand.get(i);
        tree.expandPath(path);
      }
      tree.addSelectionPaths((TreePath[])selectionPaths.toArray(new TreePath[selectionPaths.size()]));
    }
    else{
      for(int i = 0; i < pathsToExpand.size(); i++){
        Object element = pathsToExpand.get(i);
        treeBuilder.buildNodeForElement(element);
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node != null){
          tree.expandPath(new TreePath(node.getPath()));
        }
      }
      for(int i = 0; i < selectionPaths.size(); i++){
        Object element = selectionPaths.get(i);
        DefaultMutableTreeNode node = treeBuilder.getNodeForElement(element);
        if (node != null){
          DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
          tree.addSelectionPath(new TreePath(treeModel.getPathToRoot(node)));
        }
      }
    }
  }

  public static boolean isNodeOrChildSelected(JTree tree, DefaultMutableTreeNode node){
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths == null || selectionPaths.length == 0) return false;

    TreePath path = new TreePath(node.getPath());
    for(int i = 0; i < selectionPaths.length; i++){
      TreePath selectionPath = selectionPaths[i];
      if (path.isDescendant(selectionPath)) return true;
    }

    return false;
  }
}
