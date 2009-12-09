/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;

import java.util.ArrayList;
import java.util.List;

/**
* @author nik
*/
public class BuildAllArtifactsAction extends AnAction {
  public BuildAllArtifactsAction() {
    super("Build All Artifacts", "Build all configured artifacts", null);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    List<Artifact> toBuild = new ArrayList<Artifact>();
    for (Artifact artifact : ArtifactManager.getInstance(project).getSortedArtifacts()) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        toBuild.add(artifact);
      }
    }
    CompilerManager.getInstance(project).make(ArtifactCompileScope.createArtifactsScope(project, toBuild), null);
  }
}
