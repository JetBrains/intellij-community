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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class BuildArtifactActionGroup extends ActionGroup {
  public BuildArtifactActionGroup() {
    super("Build Artifact", true);
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return EMPTY_ARRAY;

    final Artifact[] artifacts = ArtifactManager.getInstance(project).getSortedArtifacts();
    List<AnAction> actions = new ArrayList<AnAction>();
    for (Artifact artifact : artifacts) {
      if (!StringUtil.isEmpty(artifact.getOutputPath())) {
        actions.add(new BuildArtifactAction(project, artifact));
      }
    }
    if (actions.size() > 1) {
      actions.add(0, new BuildAllArtifactsAction());
      actions.add(1, Separator.getInstance());
    }
    return actions.toArray(new AnAction[actions.size()]);
  }

}
