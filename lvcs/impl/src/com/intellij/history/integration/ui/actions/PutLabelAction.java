package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.ui.views.PutLabelDialog;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;

public class PutLabelAction extends LocalHistoryActionWithDialog {
  @Override
  protected DialogWrapper createDialog(IdeaGateway gw, VirtualFile f, AnActionEvent e) {
    return new PutLabelDialog(gw, f);
  }
}