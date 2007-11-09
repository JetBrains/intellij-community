package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;

public class CollapseAllAction extends TreeCollapseAllActionBase {
  protected TreeExpander getExpander(DataContext dataContext) {
    return (TreeExpander)dataContext.getData(DataConstantsEx.TREE_EXPANDER);
  }
}
