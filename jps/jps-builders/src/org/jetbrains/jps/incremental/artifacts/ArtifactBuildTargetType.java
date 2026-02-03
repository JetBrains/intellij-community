// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.artifacts;

import org.jetbrains.jps.model.artifact.JpsArtifact;

public final class ArtifactBuildTargetType extends ArtifactBasedBuildTargetType<ArtifactBuildTarget> {
  public static final ArtifactBuildTargetType INSTANCE = new ArtifactBuildTargetType();

  public ArtifactBuildTargetType() {
    super("artifact", true);
  }

  @Override
  protected ArtifactBuildTarget createArtifactBasedTarget(JpsArtifact artifact) {
    return new ArtifactBuildTarget(artifact);
  }
}
