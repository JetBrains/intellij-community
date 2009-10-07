package com.intellij.packaging.artifacts;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.packaging.elements.CompositePackagingElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public interface Artifact extends UserDataHolder {
  @NotNull
  ArtifactType getArtifactType();

  String getName();

  boolean isBuildOnMake();

  @NotNull
  CompositePackagingElement<?> getRootElement();

  @Nullable
  String getOutputPath();

  Collection<? extends ArtifactPropertiesProvider> getPropertiesProviders();

  ArtifactProperties<?> getProperties(@NotNull ArtifactPropertiesProvider propertiesProvider);

}
