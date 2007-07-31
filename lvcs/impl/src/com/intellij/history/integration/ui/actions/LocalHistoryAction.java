package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryComponent;
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
    if (getProject(e) == null) {
      p.setVisible(false);
      p.setEnabled(false);
      return;
    }
    p.setText(getText(e), true);
    p.setEnabled(isEnabled(getVcs(e), getGateway(e), getFile(e), e));
  }

  protected String getText(AnActionEvent e) {
    return e.getPresentation().getTextWithMnemonic();
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
    VirtualFile[] ff = getFiles(e);
    return (ff == null || ff.length != 1) ? null : ff[0];
  }

  private VirtualFile[] getFiles(AnActionEvent e) {
    return e.getData(DataKeys.VIRTUAL_FILE_ARRAY);
  }

  private Project getProject(AnActionEvent e) {
    return e.getData(DataKeys.PROJECT);
  }
}
