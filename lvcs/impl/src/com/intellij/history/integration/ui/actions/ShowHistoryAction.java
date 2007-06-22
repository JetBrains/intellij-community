package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.DirectoryHistoryDialog;
import com.intellij.history.integration.ui.views.FileHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public class ShowHistoryAction extends LocalHistoryActionWithDialog {
  @Override
  protected DialogWrapper createDialog(IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return f.isDirectory() ? new DirectoryHistoryDialog(gw, f) : new FileHistoryDialog(gw, f);
  }

  @Override
  protected boolean isEnabled(ILocalVcs vcs, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return f != null && gw.getFileFilter().isAllowedAndUnderContentRoot(f);
  }
}
