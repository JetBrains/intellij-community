package com.intellij.ide;

import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;

/**
 * @author yole
*/
public class DefaultTreeExpander implements TreeExpander {
  private JTree myTree;

  public DefaultTreeExpander(final JTree tree) {
    myTree = tree;
  }

  public void expandAll() {
    TreeUtil.expandAll(myTree);
  }

  public boolean canExpand() {
    return true;
  }

  public void collapseAll() {
    TreeUtil.collapseAll(myTree, 0);
  }

  public boolean canCollapse() {
    return true;
  }
}