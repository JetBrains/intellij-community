package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.ide.TreeExpander;

import javax.swing.*;

class TestTreeExpander implements TreeExpander {
  private JUnitRunningModel myModel;

  public void setModel(final JUnitRunningModel model) {
    myModel = model;
    model.addListener(new JUnitAdapter() {
      public void doDispose() {
        myModel = null;
      }
    });
  }

  public void expandAll() {
    final JTree treeView = myModel.getTreeView();
    for (int i = 0; i < treeView.getRowCount(); i++)
      treeView.expandRow(i);
  }

  public boolean canExpand() {
    return treeHasMoreThanOneLevel();
  }

  public void collapseAll() {
    final TestProxy root = myModel.getRoot();
    for (int i = 0; i < root.getChildCount(); i++)
      myModel.collapse(root.getChildAt(i));
  }

  public boolean canCollapse() {
    return treeHasMoreThanOneLevel();
  }

  private boolean treeHasMoreThanOneLevel() {
    return myModel != null && myModel.getRoot().hasChildSuites();
  }
}
