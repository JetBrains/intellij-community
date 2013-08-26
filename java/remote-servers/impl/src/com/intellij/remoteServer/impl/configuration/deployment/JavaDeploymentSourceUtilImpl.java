/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.JavaDeploymentSourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public class JavaDeploymentSourceUtilImpl extends JavaDeploymentSourceUtil {
  @NotNull
  @Override
  public ArtifactDeploymentSource createArtifactDeploymentSource(@NotNull ArtifactPointer artifactPointer) {
    return new ArtifactDeploymentSourceImpl(artifactPointer);
  }

  @NotNull
  @Override
  public List<DeploymentSource> createArtifactDeploymentSources(@NotNull Project project,
                                                                @NotNull Collection<? extends Artifact> artifacts) {
    List<DeploymentSource> sources = new ArrayList<DeploymentSource>();
    ArtifactPointerManager pointerManager = ArtifactPointerManager.getInstance(project);
    for (Artifact artifact : artifacts) {
      sources.add(createArtifactDeploymentSource(pointerManager.createPointer(artifact)));
    }
    return sources;
  }
}
