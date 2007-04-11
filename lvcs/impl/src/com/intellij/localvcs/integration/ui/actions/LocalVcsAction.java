package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalVcsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile f = getFile(e);
    Project p = getProject(e);

    IdeaGateway gw = new IdeaGateway(p);
    DialogWrapper d = createDialog(gw, f);
    d.show();
  }

  protected abstract DialogWrapper createDialog(IdeaGateway gw, VirtualFile f);

  protected VirtualFile getFile(AnActionEvent e) {
    return e.getData(DataKeys.VIRTUAL_FILE);
  }

  protected Project getProject(AnActionEvent e) {
    return e.getData(DataKeys.PROJECT);
  }
}
