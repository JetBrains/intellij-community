package com.intellij.ide.actions;

import com.intellij.ide.TreeExpander;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class CollapseAllToolbarAction extends TreeCollapseAllActionBase {
  private TreeExpander myTreeExpander;

  public CollapseAllToolbarAction(TreeExpander treeExpander) {
    myTreeExpander = treeExpander;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_COLLAPSE_ALL));
  }

  public CollapseAllToolbarAction(TreeExpander treeExpander, String description) {
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
