package com.intellij.localvcs.integration.ui.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;

public class LocalHistoryGroup extends DefaultActionGroup {
  public void update(AnActionEvent event) {
    Presentation p = event.getPresentation();
    boolean hasProject = event.getData(DataKeys.PROJECT) != null;

    p.setVisible(hasProject);
    p.setEnabled(hasProject);
  }
}

