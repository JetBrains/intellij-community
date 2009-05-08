package com.intellij.packaging.artifacts;

import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface Artifact {
  @NotNull
  ArtifactType getArtifactType();

  String getName();

  boolean isBuildOnMake();

  @NotNull
  ArtifactRootElement<?> getRootElement();

  @Nullable
  String getOutputPath();
}
