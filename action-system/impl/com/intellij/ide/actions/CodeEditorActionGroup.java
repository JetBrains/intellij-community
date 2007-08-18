package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public class CodeEditorActionGroup extends DefaultActionGroup {
  public CodeEditorActionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      presentation.setVisible(false);
      return;
    }
    boolean active = ToolWindowManager.getInstance(project).isEditorComponentActive();
    presentation.setVisible(active);
  }
}