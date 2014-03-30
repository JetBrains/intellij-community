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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.util.CloudMultiSourceServerRuntimeInstance;
import com.intellij.remoteServer.util.CloudDeploymentNameConfiguration;
import com.intellij.remoteServer.util.CloudDeploymentRuntime;
import com.intellij.remoteServer.util.ServerRuntimeException;

import java.io.File;

/**
 * @author michael.golubev
 */
public abstract class RepositoryArtifactDeploymentRuntimeProviderBase extends ArtifactDeploymentRuntimeProviderBase {

  @Override
  protected CloudDeploymentRuntime doCreateDeploymentRuntime(ArtifactDeploymentSource artifactSource,
                                                             File artifactFile,
                                                             CloudMultiSourceServerRuntimeInstance serverRuntime,
                                                             DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                             DeploymentLogManager logManager) throws ServerRuntimeException {
    RepositoryDeploymentConfiguration config = (RepositoryDeploymentConfiguration)deploymentTask.getConfiguration();

    String repositoryPath = config.getRepositoryPath();
    File repositoryRootFile;
    if (StringUtil.isEmpty(repositoryPath)) {
      File repositoryParentFolder = new File(PathManager.getSystemPath(), "cloud-git-artifact-deploy");
      repositoryRootFile = FileUtil.findSequentNonexistentFile(repositoryParentFolder, artifactFile.getName(), "");
    }
    else {
      repositoryRootFile = new File(repositoryPath);
    }

    if (!FileUtil.createDirectory(repositoryRootFile)) {
      throw new ServerRuntimeException("Unable to create deploy folder: " + repositoryRootFile);
    }
    config.setRepositoryPath(repositoryRootFile.getAbsolutePath());
    return doCreateDeploymentRuntime(artifactSource, artifactFile, serverRuntime, deploymentTask, logManager, repositoryRootFile);
  }

  protected abstract CloudDeploymentRuntime doCreateDeploymentRuntime(ArtifactDeploymentSource artifactSource,
                                                                      File artifactFile,
                                                                      CloudMultiSourceServerRuntimeInstance serverRuntime,
                                                                      DeploymentTask<? extends CloudDeploymentNameConfiguration> deploymentTask,
                                                                      DeploymentLogManager logManager,
                                                                      File repositoryRootFile) throws ServerRuntimeException;
}
