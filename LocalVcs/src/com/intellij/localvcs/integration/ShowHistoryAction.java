package com.intellij.localvcs.integration;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;

public class ShowHistoryAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    FileHistoryDialog d = new FileHistoryDialog(e.getData(DataKeys.VIRTUAL_FILE),
                                                e.getData(DataKeys.PROJECT));
    d.show();
  }
}
