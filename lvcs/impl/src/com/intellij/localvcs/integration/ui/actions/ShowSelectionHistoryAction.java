package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.views.SelectionHistoryDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsSelection;
import com.intellij.vcsUtil.VcsUtil;

public class ShowSelectionHistoryAction extends ShowHistoryAction {
  @Override
  protected DialogWrapper createDialog(IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    VcsContext c = VcsContextWrapper.createCachedInstanceOn(e);
    VcsSelection sel = VcsUtil.getSelection(c);

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    return new SelectionHistoryDialog(gw, f, from, to);
  }

  @Override
  protected boolean isEnabled(ILocalVcs vcs, IdeaGateway gw, VirtualFile f) {
    return super.isEnabled(vcs, gw, f) && !f.isDirectory();
  }
}
