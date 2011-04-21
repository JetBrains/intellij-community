/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactsWorkspaceSettings;
import com.intellij.packaging.impl.ui.ChooseArtifactsDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class BuildArtifactActionBase extends AnAction {
  private final String myActionName;

  public BuildArtifactActionBase(String actionName) {
    super(actionName + " Artifact");
    myActionName = actionName;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (project == null) {
      return;
    }
    final List<Artifact> artifacts = getArtifactWithOutputPaths(project);
    if (artifacts.isEmpty()) {
      return;
    }
    presentation.setEnabled(true);
    if (artifacts.size() == 1) {
      String first = StringUtil.first(artifacts.get(0).getName(), 40, true);
      presentation.setText(myActionName + " '" + first + "' artifact");
    }
    else {
      presentation.setText(myActionName + " Artifacts...");
    }
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    final List<Artifact> artifacts = getArtifactWithOutputPaths(project);
    if (artifacts.isEmpty()) return;

    if (artifacts.size() == 1) {
      performAction(project, artifacts);
      return;
    }

    final ChooseArtifactsDialog dialog = new ChooseArtifactsDialog(project, artifacts, "Choose Artifacts to " + myActionName,
                                                                   getDescription());
    final List<Artifact> initialSelection = ArtifactsWorkspaceSettings.getInstance(project).getArtifactsToBuild();
    if (!initialSelection.isEmpty()) {
      dialog.selectElements(initialSelection);
    }
    dialog.show();

    if (dialog.isOK()) {
      final List<Artifact> selected = dialog.getChosenElements();
      ArtifactsWorkspaceSettings.getInstance(project).setArtifactsToBuild(selected);
      performAction(project, selected);
    }
  }

  protected abstract String getDescription();

  protected abstract void performAction(Project project, List<Artifact> artifacts);

  private static List<Artifact> getArtifactWithOutputPaths(Project project) {
    final List<Artifact> result = new ArrayList<Artifact>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getSortedArtifacts()) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        result.add(artifact);
      }
    }
    return result;
  }
}
