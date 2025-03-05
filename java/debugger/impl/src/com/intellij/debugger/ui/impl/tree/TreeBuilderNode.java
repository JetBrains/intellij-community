// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.impl.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

public abstract class TreeBuilderNode extends DefaultMutableTreeNode {
  private boolean myChildrenBuilt = false;

  public TreeBuilderNode(Object userObject) {
    super(userObject);
  }

  protected abstract TreeBuilder getTreeBuilder();

  private void checkChildren() {
    synchronized (this) {
      if (myChildrenBuilt) {
        return;
      }
      myChildrenBuilt = true;
    }
    final TreeBuilder treeBuilder = getTreeBuilder();
    if (treeBuilder.isExpandable(this)) {
      treeBuilder.buildChildren(this);
    }
  }

  public void clear() {
    synchronized (this) {
      myChildrenBuilt = false;
    }
  }

  //TreeNode interface
  @Override
  public int getChildCount() {
    checkChildren();
    return super.getChildCount();
  }

  @Override
  public boolean getAllowsChildren() {
    checkChildren();
    return super.getAllowsChildren();
  }

  @Override
  public boolean isLeaf() {
    return !getTreeBuilder().isExpandable(this);
  }

  @Override
  public Enumeration children() {
    checkChildren();
    return super.children();
  }

  public Enumeration rawChildren() {
    return super.children();
  }

  @Override
  public TreeNode getChildAt(int childIndex) {
    checkChildren();
    return super.getChildAt(childIndex);
  }

  @Override
  public int getIndex(TreeNode node) {
    checkChildren();
    return super.getIndex(node);
  }
}
