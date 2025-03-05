// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArtifactDependencyNode {
  private final Artifact myArtifact;
  private final List<ArtifactDependencyNode> myDependencies;
  private final boolean myRejected;

  public ArtifactDependencyNode(@NotNull Artifact artifact, @NotNull List<ArtifactDependencyNode> dependencies, boolean rejected) {
    myArtifact = artifact;
    myDependencies = dependencies;
    myRejected = rejected;
  }

  public @NotNull Artifact getArtifact() {
    return myArtifact;
  }

  public @NotNull List<ArtifactDependencyNode> getDependencies() {
    return myDependencies;
  }

  /**
   * Returns {@code true} if this dependency was rejected by conflict resolution process
   */
  public boolean isRejected() {
    return myRejected;
  }
}
