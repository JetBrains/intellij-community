/*
 * User: anna
 * Date: 16-Jan-2008
 */
package com.intellij.packageDependencies.ui;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public class TreeModel extends DefaultTreeModel {
  private final int myMarkedFileCount;
  private final int myTotalFileCount;

  public TreeModel(TreeNode root, int total, int marked) {
    super(root);
    myMarkedFileCount = marked;
    myTotalFileCount = total;
  }

  public int getMarkedFileCount() {
    return myMarkedFileCount;
  }

  public int getTotalFileCount() {
    return myTotalFileCount;
  }
}