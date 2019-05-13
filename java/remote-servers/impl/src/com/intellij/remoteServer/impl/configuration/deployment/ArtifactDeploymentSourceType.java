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

/**
 * @author nik
 */
public class ArtifactDeploymentSourceType extends DeploymentSourceType<ArtifactDeploymentSource> {
  private static final String NAME_ATTRIBUTE = "name";

  public ArtifactDeploymentSourceType() {
    super("artifact");
  }

  @NotNull
  @Override
  public ArtifactDeploymentSource load(@NotNull Element tag, @NotNull Project project) {
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
