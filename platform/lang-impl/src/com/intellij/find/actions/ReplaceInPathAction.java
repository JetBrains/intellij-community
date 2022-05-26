// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.actions;

import com.intellij.find.replaceInProject.ReplaceInProjectManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ReplaceInPathAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    ReplaceInProjectManager replaceManager = ReplaceInProjectManager.getInstance(project);
    if (!replaceManager.isEnabled()) {
      FindInPathAction.showNotAvailableMessage(e, project);
      return;
    }

    replaceManager.replaceInProject(dataContext, null);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    FindInPathAction.doUpdate(event);
  }
}
