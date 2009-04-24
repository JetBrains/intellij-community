package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface ModifiableArtifactModel extends ArtifactModel {

  @NotNull
  ModifiableArtifact addArtifact(final String name);

  void removeArtifact(@NotNull Artifact artifact);

  @NotNull
  ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact);

  boolean isModified();

  void commit();

}
