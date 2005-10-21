
package com.intellij.ide.actions;

import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class BackAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;
    IdeDocumentHistory.getInstance(project).back();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(IdeDocumentHistory.getInstance(project).isBackAvailable());
  }
}