package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface ArtifactListener extends EventListener {

  void artifactAdded(@NotNull Artifact artifact);

  void artifactRemoved(@NotNull Artifact artifact);

  void artifactChanged(@NotNull Artifact artifact, @NotNull String oldName);

}
