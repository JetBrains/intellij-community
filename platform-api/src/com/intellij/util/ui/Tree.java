package com.intellij.util.ui;

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;

/**
 * @deprecated
 * @see com.intellij.ui.treeStructure.Tree  
 */
public class Tree extends com.intellij.ui.treeStructure.Tree {

  public Tree() {
  }

  public Tree(TreeModel treemodel) {
    super(treemodel);
  }

  public Tree(TreeNode root) {
    super(root);
  }
}