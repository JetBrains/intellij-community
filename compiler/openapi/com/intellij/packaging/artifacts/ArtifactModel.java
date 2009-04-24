package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface ArtifactModel {
  @NotNull
  Artifact[] getArtifacts();

  @Nullable
  Artifact findArtifact(@NotNull String name);

  @NotNull
  Artifact getModifiableOrOriginal(@NotNull Artifact artifact);
}
