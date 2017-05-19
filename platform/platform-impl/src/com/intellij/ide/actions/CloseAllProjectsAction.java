/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.projectImport.ProjectAttachProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * Closes all project windows and restore the Welcome Frame.
 *
 * @author Bradley M Handy &lt;brad.handy@gmail.com&gt;
 */
public class CloseAllProjectsAction extends AnAction implements DumbAware {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project currentProject = CommonDataKeys.PROJECT.getData(e.getDataContext());
    assert currentProject != null;

    ProjectManager manager = ProjectManager.getInstance();

    for (Project project : manager.getOpenProjects()) {
      if (currentProject == project) {
        continue;
      }

      ProjectUtil.closeAndDispose(project);
    }

    // force the current project to be closed last.
    ProjectUtil.closeAndDispose(currentProject);
    RecentProjectsManagerBase.getInstance().updateLastProjectPath();
    WelcomeFrame.showIfNoProjectOpened();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    ProjectManager manager = ProjectManager.getInstance();

    presentation.setEnabled(project != null && manager.getOpenProjects().length > 1);
    if (ProjectAttachProcessor.canAttachToProject() && project != null && ModuleManager.getInstance(project).getModules().length > 1) {
      presentation.setText("Close Projects in _All Windows");
    }
    else {
      presentation.setText("Close _All Projects");
    }
  }

}
