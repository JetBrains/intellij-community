package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class LocalHistoryActionWithDialog extends LocalHistoryAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DialogWrapper d = createDialog(getGateway(e), getFile(e));
    d.show();
  }

  protected abstract DialogWrapper createDialog(IdeaGateway gw, VirtualFile f);
}
