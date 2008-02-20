package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.CellTransform;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.ui.content.Content;

public class RestoreViewAction extends AnAction {

  private Content myContent;
  private CellTransform.Restore myRestoreAction;

  public RestoreViewAction(final Content content, CellTransform.Restore restore) {
    myContent = content;
    myRestoreAction = restore;
  }

  public void update(final AnActionEvent e) {
    Presentation p = e.getPresentation();
    p.setText(ActionsBundle.message("action.Runner.RestoreView.text", myContent.getDisplayName()));
    p.setDescription(ActionsBundle.message("action.Runner.RestoreView.description"));
    p.setIcon(myContent.getIcon());
  }

  public void actionPerformed(final AnActionEvent e) {
    myRestoreAction.restoreInGrid();
  }
}
