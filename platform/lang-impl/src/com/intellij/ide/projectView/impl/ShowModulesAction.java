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
package com.intellij.ide.projectView.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since 8/5/11
 */
public abstract class ShowModulesAction extends ToggleAction {
  private final Project myProject;

  public ShowModulesAction(Project project) {
    super(IdeBundle.message("action.show.modules"), IdeBundle.message("action.description.show.modules"),
          AllIcons.ObjectBrowser.ShowModules);
    myProject = project;
  }

  @Override
  public boolean isSelected(AnActionEvent event) {
    return ProjectView.getInstance(myProject).isShowModules(getId());
  }

  @NotNull
  protected abstract String getId();

  @Override
  public void setSelected(AnActionEvent event, boolean flag) {
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
    projectView.setShowModules(flag, getId());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    final ProjectViewImpl projectView = (ProjectViewImpl)ProjectView.getInstance(myProject);
    presentation.setVisible(hasModules() && Comparing.strEqual(projectView.getCurrentViewId(), getId()));
  }

  public static boolean hasModules() {
    return PlatformUtils.isIntelliJ();
  }
}
