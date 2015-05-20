/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.profile.Profile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuickChangeInspectionProfileAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    final InspectionProjectProfileManager projectProfileManager = InspectionProjectProfileManager.getInstance(project);
    InspectionProfile current = projectProfileManager.getInspectionProfile();
    for (Profile profile : projectProfileManager.getProfiles()) {
      addScheme(group, projectProfileManager, current, profile);
    }
  }

  private static void addScheme(final DefaultActionGroup group,
                                final InspectionProjectProfileManager projectProfileManager, 
                                final Profile current,
                                final Profile scheme) {
    group.add(new DumbAwareAction(scheme.getName(), "", scheme == current ? ourCurrentAction : ourNotCurrentAction) {
      @Override
      public void actionPerformed(@Nullable AnActionEvent e) {
        projectProfileManager.setProjectProfile(scheme.getName());
      }
    });
  }

  @Override
  protected boolean isEnabled() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    e.getPresentation().setEnabledAndVisible(project != null && InspectionProjectProfileManager.getInstance(project).getProfiles().size() > 1);
  }
}
