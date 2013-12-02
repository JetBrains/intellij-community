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
import com.intellij.remoteServer.util.ServerRuntimeException;

import java.io.File;

/**
 * @author michael.golubev
 */
public abstract class RepositoryArtifactDeploymentSourceHandlerBase extends ArtifactDeploymentSourceHandlerBase {

  private final File myRepositoryRootFile;

  public RepositoryArtifactDeploymentSourceHandlerBase(ArtifactDeploymentSource deploymentSource, RepositoryDeploymentConfiguration config)
    throws ServerRuntimeException {
    super(deploymentSource);
    String repositoryPath = config.getRepositoryPath();
    if (StringUtil.isEmpty(repositoryPath)) {
      File repositoryParentFolder = new File(PathManager.getSystemPath(), "cloud-git-artifact-deploy");
      myRepositoryRootFile = FileUtil.findSequentNonexistentFile(repositoryParentFolder, getArtifactFile().getName(), "");
    }
    else {
      myRepositoryRootFile = new File(repositoryPath);
    }

    if (!FileUtil.createDirectory(myRepositoryRootFile)) {
      throw new ServerRuntimeException("Unable to create deploy folder: " + myRepositoryRootFile);
    }
    config.setRepositoryPath(myRepositoryRootFile.getAbsolutePath());
  }

  @Override
  public File getRepositoryRootFile() {
    return myRepositoryRootFile;
  }
}
