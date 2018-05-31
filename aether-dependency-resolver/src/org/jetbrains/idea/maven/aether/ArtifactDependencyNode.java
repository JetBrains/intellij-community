// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.artifact.Artifact;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArtifactDependencyNode {
  private final Artifact myArtifact;
  private final List<ArtifactDependencyNode> myDependencies;

  public ArtifactDependencyNode(@NotNull Artifact artifact, @NotNull List<ArtifactDependencyNode> dependencies) {
    myArtifact = artifact;
    myDependencies = dependencies;
  }

  @NotNull
  public Artifact getArtifact() {
    return myArtifact;
  }

  @NotNull
  public List<ArtifactDependencyNode> getDependencies() {
    return myDependencies;
  }
}
