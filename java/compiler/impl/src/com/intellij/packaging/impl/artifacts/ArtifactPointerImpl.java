// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
import org.jetbrains.annotations.NotNull;

public final class ArtifactPointerImpl implements ArtifactPointer {
  private String myName;
  private Artifact myArtifact;

  public ArtifactPointerImpl(@NotNull String name) {
    myName = name;
  }

  public ArtifactPointerImpl(@NotNull Artifact artifact) {
    myArtifact = artifact;
    myName = artifact.getName();
  }

  @Override
  public @NotNull String getArtifactName() {
    return myName;
  }

  @Override
  public Artifact getArtifact() {
    return myArtifact;
  }

  @Override
  public @NotNull String getArtifactName(@NotNull ArtifactModel artifactModel) {
    if (myArtifact != null) {
      return artifactModel.getArtifactByOriginal(myArtifact).getName();
    }
    return myName;
  }

  @Override
  public Artifact findArtifact(@NotNull ArtifactModel artifactModel) {
    if (myArtifact != null) {
      return artifactModel.getArtifactByOriginal(myArtifact);
    }
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(Artifact artifact) {
    myArtifact = artifact;
  }

  void setName(String name) {
    myName = name;
  }
}
