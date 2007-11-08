package com.intellij.ui;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;

public class SelectionSaver implements TreeSelectionListener, TreeModelListener, PropertyChangeListener{

  private final JTree myTree;
  private Collection<TreeNode> myCurrentSelection = new ArrayList<TreeNode>();


  private SelectionSaver(JTree tree) {
    myTree = tree;
  }

  public static void installOn(JTree tree){
    new SelectionSaver(tree).install();
  }

  private void install(){
    myTree.getModel().addTreeModelListener(this);
    myTree.getSelectionModel().addTreeSelectionListener(this);
    myTree.addPropertyChangeListener(this);
  }

  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals(JTree.TREE_MODEL_PROPERTY)){
      ((TreeModel)evt.getOldValue()).removeTreeModelListener(this);
      ((TreeModel)evt.getNewValue()).addTreeModelListener(this);
    }
  }

  public void treeNodesRemoved(TreeModelEvent treeModelEvent) {
    TreePath pathToDelete = treeModelEvent.getTreePath();
    Object[] children = treeModelEvent.getChildren();

    for (int i = 0; i < children.length; i++) {
      Object nodeToDelete = children[i];
      if (myCurrentSelection.contains(nodeToDelete)){
        int deletedRow = myTree.getRowForPath(pathToDelete.pathByAddingChild(nodeToDelete));
        if (deletedRow == 0) return;
        TreePath treePath = new TreePath(myTree.getPathForRow(deletedRow - 1));
        myTree.getSelectionModel().addSelectionPath((TreePath)treePath.getLastPathComponent());
      }
    }

  }

  public void valueChanged(TreeSelectionEvent e) {
    myCurrentSelection = new ArrayList<TreeNode>();
    TreePath[] selection = myTree.getSelectionModel().getSelectionPaths();
    if (selection == null) return;
    for (int i = 0; i < selection.length; i++) {
      TreePath treePath = selection[i];
      myCurrentSelection.add((TreeNode)treePath.getLastPathComponent());
    }
  }

  public void treeNodesChanged(TreeModelEvent e) {

  }

  public void treeNodesInserted(TreeModelEvent e) {

  }

  public void treeStructureChanged(TreeModelEvent e) {

  }
}
