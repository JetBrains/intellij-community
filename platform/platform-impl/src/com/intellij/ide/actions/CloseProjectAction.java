/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.ui.IdeUICustomization;

public class CloseProjectAction extends AnAction implements DumbAware {
  public CloseProjectAction() {
    getTemplatePresentation().setText(IdeUICustomization.getInstance().getCloseProjectActionText());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    ProjectUtil.closeAndDispose(project);
    WelcomeFrame.showIfNoProjectOpened();
  }

  @Override
  public void update(AnActionEvent event){
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
