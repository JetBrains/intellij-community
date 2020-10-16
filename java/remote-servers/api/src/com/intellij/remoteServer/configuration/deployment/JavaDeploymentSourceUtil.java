// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.configuration.deployment;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class JavaDeploymentSourceUtil {
  public static JavaDeploymentSourceUtil getInstance() {
    return ApplicationManager.getApplication().getService(JavaDeploymentSourceUtil.class);
  }

  @NotNull
  public abstract ArtifactDeploymentSource createArtifactDeploymentSource(@NotNull ArtifactPointer artifactPointer);

  @NotNull
  public abstract List<DeploymentSource> createArtifactDeploymentSources(@NotNull Project project,
                                                                         @NotNull Collection<? extends Artifact> artifacts);

  @NotNull
  public abstract List<DeploymentSource> createArtifactDeploymentSources(Project project, ArtifactType... artifactTypes);
}
