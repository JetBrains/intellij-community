// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactRootDescriptor;
import org.jetbrains.jps.model.artifact.JpsArtifact;

public abstract class ArtifactBasedBuildTarget extends BuildTarget<ArtifactRootDescriptor> {
  private final JpsArtifact artifact;

  protected ArtifactBasedBuildTarget(@NotNull BuildTargetType<? extends BuildTarget<ArtifactRootDescriptor>> targetType,
                                     @NotNull JpsArtifact artifact) {
    super(targetType);
    this.artifact = artifact;
  }

  @Override
  public @NotNull String getId() {
    return artifact.getName();
  }

  public JpsArtifact getArtifact() {
    return artifact;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return artifact.equals(((ArtifactBasedBuildTarget)o).artifact);
  }

  @Override
  public int hashCode() {
    return artifact.hashCode();
  }
}
