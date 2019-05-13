// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public class ShowModulePropertiesAction extends AnAction{

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module == null) {
      return;
    }
    ModulesConfigurator.showDialog(project, module.getName(), null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    e.getPresentation().setVisible(project != null && module != null);
  }
}
