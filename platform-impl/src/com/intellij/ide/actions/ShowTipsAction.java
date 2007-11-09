package com.intellij.ide.actions;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ShowTipsAction extends AnAction {
  private static TipDialog ourTipDialog;

  public void actionPerformed(AnActionEvent e) {
    if (ourTipDialog != null && ourTipDialog.isVisible()) {
      ourTipDialog.dispose();
    }
    ourTipDialog = new TipDialog();
    ourTipDialog.show();
  }
}