package com.intellij.packaging.artifacts;

import com.intellij.packaging.elements.ArtifactRootElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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

  Collection<? extends ArtifactPropertiesProvider> getPropertiesProviders();

  ArtifactProperties<?> getProperties(@NotNull ArtifactPropertiesProvider propertiesProvider);
  
}
