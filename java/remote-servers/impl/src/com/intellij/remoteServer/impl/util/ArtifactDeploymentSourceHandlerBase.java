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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.util.DeploymentSourceHandler;
import com.intellij.remoteServer.util.ServerRuntimeException;

import java.io.File;

/**
 * @author michael.golubev
 */
public abstract class ArtifactDeploymentSourceHandlerBase implements DeploymentSourceHandler {

  private static final Logger LOG = Logger.getInstance("#" + ArtifactDeploymentSourceHandlerBase.class.getName());

  private final Artifact myArtifact;
  private final File myRepositoryRootFile;

  public ArtifactDeploymentSourceHandlerBase(ArtifactDeploymentSource deploymentSource) throws ServerRuntimeException {
    Artifact artifact = deploymentSource.getArtifact();
    if (artifact == null) {
      throw new ServerRuntimeException("Artifact not found " + deploymentSource.getArtifactPointer().getArtifactName());
    }

    String outputPath = artifact.getOutputPath();
    LOG.assertTrue(outputPath != null, "Artifact output path not found");
    myRepositoryRootFile = new File(outputPath, "/deploy");
    if (!myRepositoryRootFile.exists() && !myRepositoryRootFile.mkdir()) {
      throw new ServerRuntimeException("Unable to create deploy folder");
    }
    myArtifact = artifact;
  }

  protected File getArtifactFile() {
    return new File(myArtifact.getOutputFilePath());
  }

  @Override
  public File getRepositoryRootFile() {
    return myRepositoryRootFile;
  }
}
