package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class ExpandAllToolbarAction extends TreeExpandAllActionBase {
  private TreeExpander myTreeExpander;

  public ExpandAllToolbarAction(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_EXPAND_ALL));
  }

  public ExpandAllToolbarAction(TreeExpander treeExpander, String description) {
    this(treeExpander);
    getTemplatePresentation().setDescription(description);
  }

  protected TreeExpander getExpander(DataContext dataContext) {
    return myTreeExpander;
  }

  public void setTreeExpander(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
  }
}
