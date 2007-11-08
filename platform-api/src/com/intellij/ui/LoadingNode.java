package com.intellij.ui;

import com.intellij.ide.IdeBundle;

import javax.swing.tree.DefaultMutableTreeNode;

public class LoadingNode extends DefaultMutableTreeNode {
  public LoadingNode() {
    super(IdeBundle.message("treenode.loading"));
  }

  public LoadingNode(String text) {
    super(text);
  }
}