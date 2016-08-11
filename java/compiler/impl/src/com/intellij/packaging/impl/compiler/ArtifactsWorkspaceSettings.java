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
package com.intellij.packaging.impl.compiler;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
@State(name = "ArtifactsWorkspaceSettings",
  storages = {
    @Storage(StoragePathMacros.WORKSPACE_FILE)
  })
public class ArtifactsWorkspaceSettings implements PersistentStateComponent<ArtifactsWorkspaceSettings.ArtifactsWorkspaceSettingsState> {
  private ArtifactsWorkspaceSettingsState myState = new ArtifactsWorkspaceSettingsState();
  private final Project myProject;

  public ArtifactsWorkspaceSettings(Project project) {
    myProject = project;
  }

  public static ArtifactsWorkspaceSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ArtifactsWorkspaceSettings.class);
  }

  public List<Artifact> getArtifactsToBuild() {
    final List<Artifact> result = new ArrayList<>();
    final ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
    for (String name : myState.myArtifactsToBuild) {
      ContainerUtil.addIfNotNull(result, artifactManager.findArtifact(name));
    }
    return result;
  }

  public void setArtifactsToBuild(@NotNull Collection<? extends Artifact> artifacts) {
    myState.myArtifactsToBuild.clear();
    for (Artifact artifact : artifacts) {
      myState.myArtifactsToBuild.add(artifact.getName());
    }
    Collections.sort(myState.myArtifactsToBuild);
  }

  public ArtifactsWorkspaceSettingsState getState() {
    return myState;
  }

  public void loadState(ArtifactsWorkspaceSettingsState state) {
    myState = state;
  }

  public static class ArtifactsWorkspaceSettingsState {
    @Tag("artifacts-to-build")
    @AbstractCollection(surroundWithTag = false, elementTag = "artifact", elementValueAttribute = "name")
    public List<String> myArtifactsToBuild = new ArrayList<>();

  }
}
