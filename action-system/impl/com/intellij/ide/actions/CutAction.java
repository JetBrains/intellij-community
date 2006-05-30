
package com.intellij.ide.actions;

import com.intellij.ide.CutProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

public class CutAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    CutProvider provider = (CutProvider)dataContext.getData(DataConstants.CUT_PROVIDER);
    if (provider == null) return;
    provider.performCut(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    CutProvider provider = (CutProvider)dataContext.getData(DataConstants.CUT_PROVIDER);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null && project.isOpen() && provider != null && provider.isCutEnabled(dataContext));
  }
}
