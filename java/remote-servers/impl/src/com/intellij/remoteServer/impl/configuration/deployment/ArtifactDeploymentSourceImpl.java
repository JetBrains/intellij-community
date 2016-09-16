package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.remoteServer.configuration.deployment.ArtifactDeploymentSource;
import com.intellij.remoteServer.configuration.deployment.DeploymentSourceType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;

/**
 * @author nik
 */
public class ArtifactDeploymentSourceImpl implements ArtifactDeploymentSource {
  private final ArtifactPointer myPointer;

  public ArtifactDeploymentSourceImpl(@NotNull ArtifactPointer pointer) {
    myPointer = pointer;
  }

  @NotNull
  @Override
  public ArtifactPointer getArtifactPointer() {
    return myPointer;
  }

  @Override
  public Artifact getArtifact() {
    return myPointer.getArtifact();
  }

  @Override
  public File getFile() {
    final String path = getFilePath();
    return path != null ? new File(path) : null;
  }

  @Override
  public String getFilePath() {
    final Artifact artifact = getArtifact();
    if (artifact != null) {
      String outputPath = artifact.getOutputPath();
      if (outputPath != null) {
        final CompositePackagingElement<?> rootElement = artifact.getRootElement();
        if (!(rootElement instanceof ArtifactRootElement<?>)) {
          outputPath += "/" + rootElement.getName();
        }
        return FileUtil.toSystemDependentName(outputPath);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return myPointer.getArtifactName();
  }

  @Override
  public Icon getIcon() {
    final Artifact artifact = getArtifact();
    return artifact != null ? artifact.getArtifactType().getIcon() : null;
  }

  @Override
  public boolean isValid() {
    return getArtifact() != null;
  }

  @Override
  public boolean isArchive() {
    Artifact artifact = getArtifact();
    return artifact != null && !(artifact.getRootElement() instanceof ArtifactRootElement<?>);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ArtifactDeploymentSourceImpl)) return false;

    return myPointer.equals(((ArtifactDeploymentSourceImpl)o).myPointer);

  }

  @Override
  public int hashCode() {
    return myPointer.hashCode();
  }

  @NotNull
  @Override
  public DeploymentSourceType<?> getType() {
    return DeploymentSourceType.EP_NAME.findExtension(ArtifactDeploymentSourceType.class);
  }
}
