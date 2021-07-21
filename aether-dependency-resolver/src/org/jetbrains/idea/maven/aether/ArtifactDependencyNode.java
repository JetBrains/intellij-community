// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public Artifact getArtifact() {
    return myArtifact;
  }

  @NotNull
  public List<ArtifactDependencyNode> getDependencies() {
    return myDependencies;
  }

  /**
   * Returns {@code true} if this dependency was rejected by conflict resolution process
   */
  public boolean isRejected() {
    return myRejected;
  }
}
