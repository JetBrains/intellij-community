package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ActionManager;

public class OtherGroup extends DefaultActionGroup {
  public OtherGroup() {
    super();
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setVisible(getChildrenCount() > 0);
  }
}
