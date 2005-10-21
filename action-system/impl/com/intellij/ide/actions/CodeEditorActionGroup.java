package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;

public class CodeEditorActionGroup extends DefaultActionGroup {
  public CodeEditorActionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setVisible(false);
      return;
    }
    boolean active = ToolWindowManager.getInstance(project).isEditorComponentActive();
    presentation.setVisible(active);
  }
}