package com.intellij.debugger.ui.content.newUI.actions;

import com.intellij.debugger.ui.content.newUI.CellTransform;
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
    p.setText(ActionsBundle.message("action.Debugger.RestoreView.text", myContent.getDisplayName()));
    p.setDescription(ActionsBundle.message("action.Debugger.RestoreView.description"));
    p.setIcon(myContent.getIcon());
  }

  public void actionPerformed(final AnActionEvent e) {
    myRestoreAction.restoreInGrid();
  }
}
