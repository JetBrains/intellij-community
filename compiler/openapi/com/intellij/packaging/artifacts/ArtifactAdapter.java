package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArtifactAdapter implements ArtifactListener {
  public void artifactAdded(@NotNull Artifact artifact) {
  }

  public void artifactRemoved(@NotNull Artifact artifact) {
  }

  public void artifactChanged(@NotNull Artifact artifact, @NotNull String oldName) {
  }
}
