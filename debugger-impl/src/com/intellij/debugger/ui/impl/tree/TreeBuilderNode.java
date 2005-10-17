package com.intellij.debugger.ui.impl.tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

/**
 * User: lex
 * Date: Sep 10, 2003
 * Time: 7:01:02 PM
 */
public abstract class TreeBuilderNode extends DefaultMutableTreeNode{
  private boolean  myChildrenBuilt = false;

  public TreeBuilderNode(Object userObject) {
    super(userObject);
  }

  abstract protected TreeBuilder getTreeBuilder();

  private void checkChildren() {
    synchronized (this) {
      if (myChildrenBuilt) {
        return;
      }
      myChildrenBuilt = true;
    }
    final TreeBuilder treeBuilder = getTreeBuilder();
    if(treeBuilder.isExpandable(this)) {
      treeBuilder.buildChildren(this);
    }
  }

  public void clear() {
    synchronized (this) {
      myChildrenBuilt = false;
    }
  }

  //TreeNode interface
  public int getChildCount() {
    checkChildren();
    return super.getChildCount();
  }

  public boolean getAllowsChildren() {
    checkChildren();
    return super.getAllowsChildren();
  }

  public boolean isLeaf() {
    return !getTreeBuilder().isExpandable(this);
  }

  public Enumeration children() {
    checkChildren();
    return super.children();
  }

  public Enumeration rawChildren() {
    return super.children();
  }

  public TreeNode getChildAt(int childIndex) {
    checkChildren();
    return super.getChildAt(childIndex);
  }

  public int getIndex(TreeNode node) {
    checkChildren();
    return super.getIndex(node);
  }
}
