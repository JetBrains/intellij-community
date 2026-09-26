// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.ui;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

public final class TreeModel extends DefaultTreeModel {
  private int myMarkedFileCount;
  private int myTotalFileCount;

  public TreeModel(TreeNode root) {
    super(root);
  }

  public TreeModel(TreeNode root, int total, int marked) {
    super(root);
    myMarkedFileCount = marked;
    myTotalFileCount = total;
  }

  public void setMarkedFileCount(int markedFileCount) {
    myMarkedFileCount = markedFileCount;
  }

  public void setTotalFileCount(int totalFileCount) {
    myTotalFileCount = totalFileCount;
  }

  public int getMarkedFileCount() {
    return myMarkedFileCount;
  }

  public int getTotalFileCount() {
    return myTotalFileCount;
  }
}