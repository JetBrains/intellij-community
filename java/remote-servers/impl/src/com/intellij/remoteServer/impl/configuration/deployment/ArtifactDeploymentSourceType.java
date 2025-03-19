// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.impl.run.BuildArtifactsBeforeRunTaskProvider;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ArtifactDeploymentSourceType extends DeploymentSourceType<ArtifactDeploymentSource> {
  private static final String NAME_ATTRIBUTE = "name";

  public ArtifactDeploymentSourceType() {
    super("artifact");
  }

  @Override
  public @NotNull ArtifactDeploymentSource load(@NotNull Element tag, @NotNull Project project) {
    return new ArtifactDeploymentSourceImpl(ArtifactPointerManager.getInstance(project).createPointer(tag.getAttributeValue(NAME_ATTRIBUTE)));
  }

  @Override
  public void save(@NotNull ArtifactDeploymentSource source, @NotNull Element tag) {
    tag.setAttribute(NAME_ATTRIBUTE, source.getArtifactPointer().getArtifactName());
  }

  @Override
  public void setBuildBeforeRunTask(@NotNull RunConfiguration configuration,
                                    @NotNull ArtifactDeploymentSource source) {
    Artifact artifact = source.getArtifact();
    if (artifact != null) {
      BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRun(configuration.getProject(), configuration, artifact);
    }
  }

  @Override
  public void updateBuildBeforeRunOption(@NotNull JComponent runConfigurationEditorComponent, @NotNull Project project,
                                         @NotNull ArtifactDeploymentSource source, boolean select) {
    Artifact artifact = source.getArtifact();
    if (artifact != null) {
      BuildArtifactsBeforeRunTaskProvider.setBuildArtifactBeforeRunOption(runConfigurationEditorComponent, project, artifact, select);
    }
  }
}
