package com.intellij.execution.testframework.actions;

import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.ide.TreeExpander;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;

public class TestTreeExpander implements TreeExpander {
  private TestFrameworkRunningModel myModel;

  public void setModel(final TestFrameworkRunningModel model) {
    myModel = model;
    model.addListener(new TestFrameworkRunningModel.ModelListener() {
      public void onDispose(final TestFrameworkRunningModel model) {
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
    TreeUtil.collapseAll(myModel.getTreeView(), 1);
  }

  public boolean canCollapse() {
    return treeHasMoreThanOneLevel();
  }

  private boolean treeHasMoreThanOneLevel() {
    return myModel != null && myModel.hasTestSuites();
  }
}
