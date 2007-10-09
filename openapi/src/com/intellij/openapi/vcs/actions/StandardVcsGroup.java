/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NonNls;

public abstract class StandardVcsGroup extends DefaultActionGroup {
  public abstract AbstractVcs getVcs(Project project);

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getData(DataKeys.PROJECT);
    presentation.setVisible(project != null &&
                            ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(getVcsName(project)));
    presentation.setEnabled(presentation.isVisible());
  }

  @NonNls
  public String getVcsName(Project project) {
    final AbstractVcs vcs = getVcs(project);
    assert vcs != null: getClass().getName() + " couldn't find VCS";
    return vcs.getName();
  }
}
