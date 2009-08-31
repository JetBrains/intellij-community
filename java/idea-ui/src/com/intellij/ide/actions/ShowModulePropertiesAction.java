package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 8, 2004
 */
public class ShowModulePropertiesAction extends AnAction{

  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      return;
    }
    final Module module = (Module)dataContext.getData(DataConstants.MODULE_CONTEXT);
    if (module == null) {
      return;
    }
    ModulesConfigurator.showDialog(project, module.getName(), null, false);
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Module module = (Module)dataContext.getData(DataConstants.MODULE_CONTEXT);
    e.getPresentation().setVisible(project != null && module != null);
  }
}
