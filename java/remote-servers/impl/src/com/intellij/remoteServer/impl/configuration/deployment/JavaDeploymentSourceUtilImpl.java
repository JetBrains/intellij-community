// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.*;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.configuration.deployment.JavaDeploymentSourceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class JavaDeploymentSourceUtilImpl extends JavaDeploymentSourceUtil {
  @Override
  public @NotNull ArtifactDeploymentSource createArtifactDeploymentSource(@NotNull ArtifactPointer artifactPointer) {
    return new ArtifactDeploymentSourceImpl(artifactPointer);
  }

  @Override
  public @NotNull List<DeploymentSource> createArtifactDeploymentSources(@NotNull Project project,
                                                                         @NotNull Collection<? extends Artifact> artifacts) {
    List<DeploymentSource> sources = new ArrayList<>();
    ArtifactPointerManager pointerManager = ArtifactPointerManager.getInstance(project);
    for (Artifact artifact : artifacts) {
      sources.add(createArtifactDeploymentSource(pointerManager.createPointer(artifact)));
    }
    return sources;
  }

  @Override
  public @NotNull List<DeploymentSource> createArtifactDeploymentSources(Project project, ArtifactType... artifactTypes) {
    if (project.isDefault()) return Collections.emptyList();
    Artifact[] artifacts = ArtifactManager.getInstance(project).getArtifacts();
    List<Artifact> supportedArtifacts = new ArrayList<>();
    Set<ArtifactType> typeSet = Set.of(artifactTypes);
    for (Artifact artifact : artifacts) {
      if (typeSet.contains(artifact.getArtifactType())) {
        supportedArtifacts.add(artifact);
      }
    }
    return createArtifactDeploymentSources(project, supportedArtifacts);
  }
}
