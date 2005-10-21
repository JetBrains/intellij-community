package com.intellij.ide.actions.tree;

import com.intellij.ui.TreeExpandCollapse;

import javax.swing.*;

public class FullyExpandTreeNodeAction extends BaseTreeNodeAction {
  protected void performOn(JTree tree) {
    TreeExpandCollapse.expandAll(tree);
  }
}
