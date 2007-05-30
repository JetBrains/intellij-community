package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.LocalHistory;
import com.intellij.localvcs.integration.LocalHistoryComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistoryAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    Presentation p = e.getPresentation();
    if (!LocalHistory.isEnabled(getProject(e))) {
      p.setVisible(false);
      return;
    }
    p.setText(getText(e));
    p.setEnabled(isEnabled(getVcs(e), getGateway(e), getFile(e), e));
  }

  protected String getText(AnActionEvent e) {
    return e.getPresentation().getText();
  }

  protected boolean isEnabled(ILocalVcs vcs, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return true;
  }

  protected ILocalVcs getVcs(AnActionEvent e) {
    return LocalHistoryComponent.getLocalVcsFor(getProject(e));
  }

  protected IdeaGateway getGateway(AnActionEvent e) {
    return new IdeaGateway(getProject(e));
  }

  protected VirtualFile getFile(AnActionEvent e) {
    return e.getData(DataKeys.VIRTUAL_FILE);
  }

  private Project getProject(AnActionEvent e) {
    return e.getData(DataKeys.PROJECT);
  }
}
