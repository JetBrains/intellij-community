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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistoryAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DialogWrapper d = createDialog(getGateway(e), getFile(e));
    d.show();
  }

  protected abstract DialogWrapper createDialog(IdeaGateway gw, VirtualFile f);

  @Override
  public void update(AnActionEvent e) {
    Presentation p = e.getPresentation();
    if (!LocalHistory.isEnabled(getProject(e))) {
      p.setVisible(false);
      return;
    }
    p.setEnabled(isEnabled(getVcs(e), getGateway(e), getFile(e)));
  }

  protected boolean isEnabled(ILocalVcs vcs, IdeaGateway gw, VirtualFile f) {
    return true;
  }

  private ILocalVcs getVcs(AnActionEvent e) {
    return LocalHistoryComponent.getLocalVcsFor(getProject(e));
  }

  private IdeaGateway getGateway(AnActionEvent e) {
    return new IdeaGateway(getProject(e));
  }

  protected VirtualFile getFile(AnActionEvent e) {
    return e.getData(DataKeys.VIRTUAL_FILE);
  }

  private Project getProject(AnActionEvent e) {
    return e.getData(DataKeys.PROJECT);
  }
}
