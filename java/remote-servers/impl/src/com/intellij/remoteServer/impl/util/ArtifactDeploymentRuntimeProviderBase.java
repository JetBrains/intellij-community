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
package com.intellij.remoteServer.impl.util;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.util.*;

import java.io.File;

/**
 * @author michael.golubev
 */
public abstract class ArtifactDeploymentRuntimeProviderBase implements CloudDeploymentRuntimeProvider {

  @Override
  public CloudDeploymentRuntime createDeploymentRuntime(DeploymentSource source,
                                                        CloudMultiSourceServerRuntimeInstance serverRuntime,
                                                        DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                        DeploymentLogManager logManager)
    throws ServerRuntimeException {
    if (!(source instanceof ArtifactDeploymentSource)) {
      return null;
    }

    ArtifactDeploymentSource artifactSource = (ArtifactDeploymentSource)source;
    Artifact artifact = artifactSource.getArtifact();
    if (artifact == null) {
      throw new ServerRuntimeException("Artifact not found " + artifactSource.getArtifactPointer().getArtifactName());
    }

    String artifactPath = artifact.getOutputFilePath();
    if (artifactPath == null) {
      throw new ServerRuntimeException("Artifact output not found");
    }

    return doCreateDeploymentRuntime(artifactSource, new File(artifactPath), serverRuntime, deploymentTask, logManager);
  }

  protected abstract CloudDeploymentRuntime doCreateDeploymentRuntime(ArtifactDeploymentSource artifactSource,
                                                                      File artifactFile,
                                                                      CloudMultiSourceServerRuntimeInstance serverRuntime,
                                                                      DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                                      DeploymentLogManager logManager) throws ServerRuntimeException;
}
