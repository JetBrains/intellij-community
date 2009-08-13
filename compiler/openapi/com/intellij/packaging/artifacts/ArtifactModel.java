package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public interface ArtifactModel {
  @NotNull
  Artifact[] getArtifacts();

  @Nullable
  Artifact findArtifact(@NotNull String name);

  @NotNull
  Artifact getArtifactByOriginal(@NotNull Artifact artifact);

  Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type);
}
