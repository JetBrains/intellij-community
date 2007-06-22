package com.intellij.history.integration.ui.actions;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.SelectionHistoryDialog;
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
    VcsSelection sel = getSelection(e);

    int from = sel.getSelectionStartLineNumber();
    int to = sel.getSelectionEndLineNumber();

    return new SelectionHistoryDialog(gw, f, from, to);
  }

  @Override
  protected String getText(AnActionEvent e) {
    VcsSelection sel = getSelection(e);
    return sel == null ? super.getText(e) : sel.getActionName();
  }

  @Override
  protected boolean isEnabled(ILocalVcs vcs, IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return super.isEnabled(vcs, gw, f, e) && !f.isDirectory() && getSelection(e) != null;
  }

  private VcsSelection getSelection(AnActionEvent e) {
    VcsContext c = VcsContextWrapper.createCachedInstanceOn(e);
    return VcsUtil.getSelection(c);
  }
}
