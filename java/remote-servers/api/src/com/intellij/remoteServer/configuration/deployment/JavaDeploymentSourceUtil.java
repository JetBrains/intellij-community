// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull ArtifactDeploymentSource createArtifactDeploymentSource(@NotNull ArtifactPointer artifactPointer);

  public abstract @NotNull List<DeploymentSource> createArtifactDeploymentSources(@NotNull Project project,
                                                                                  @NotNull Collection<? extends Artifact> artifacts);

  public abstract @NotNull List<DeploymentSource> createArtifactDeploymentSources(Project project, ArtifactType... artifactTypes);
}
