package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ArtifactPointer {

  @NotNull
  String getName();

  @Nullable
  Artifact getArtifact();

  @Nullable
  Artifact findArtifact(@NotNull ArtifactModel artifactModel);

}
