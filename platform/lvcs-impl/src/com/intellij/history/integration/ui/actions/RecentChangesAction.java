package com.intellij.history.integration.ui.actions;

import com.intellij.history.integration.ui.views.RecentChangesPopup;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class RecentChangesAction extends LocalHistoryAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new RecentChangesPopup(getGateway(e), getVcs(e)).show();
  }
}
