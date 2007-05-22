package com.intellij.localvcs.integration.ui.actions;

import com.intellij.localvcs.integration.ui.views.RecentChangesPopup;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class RecentChangesAction extends LocalHistoryAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new RecentChangesPopup(getGateway(e), getVcs(e)).show();
  }
}
