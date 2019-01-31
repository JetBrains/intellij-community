// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.ui.IdeUICustomization;
import org.jetbrains.annotations.NotNull;

public class CloseProjectAction extends AnAction implements DumbAware {
  public CloseProjectAction() {
    getTemplatePresentation().setText(IdeUICustomization.getInstance().getCloseProjectActionText());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    ProjectManagerEx.getInstanceEx().closeAndDispose(project);
    // RecentProjectsManager cannot distinguish close as part of exit (no need to remove project),
    // and close as explicit user initiated action (need to remove project), because reason is not provided to `projectClosed` event.
    RecentProjectsManager.getInstance().updateLastProjectPath();
    WelcomeFrame.showIfNoProjectOpened();
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    presentation.setEnabled(project != null);
    if (ProjectAttachProcessor.canAttachToProject() && project != null && ModuleManager.getInstance(project).getModules().length > 1) {
      presentation.setText(IdeBundle.message("action.close.projects.in.current.window"));
    }
    else {
      presentation.setText(IdeUICustomization.getInstance().getCloseProjectActionText());
    }
  }
}
