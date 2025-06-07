// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.artifacts.ArtifactPointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.packaging.artifacts.ArtifactManager.getInstance;

@ApiStatus.Internal
public final class ArtifactPointerImpl implements ArtifactPointer {
  private final @NotNull Project myProject;
  private volatile @NotNull String myName;
  private volatile AtomicReference<Artifact> myArtifact;

  ArtifactPointerImpl(@NotNull String name, @NotNull Project project) {
    myName = name;
    this.myProject = project;
  }

  @Override
  public @NotNull String getArtifactName() {
    return myName;
  }

  @Override
  public Artifact getArtifact() {
    AtomicReference<Artifact> artifact = myArtifact;
    if (artifact == null) {
      // benign race
      myArtifact = artifact = new AtomicReference<>(getInstance(myProject).findArtifact(myName));
    }
    return artifact.get();
  }

  public Artifact getArtifactNoResolve() {
    AtomicReference<Artifact> artifact = myArtifact;
    return artifact == null ? null : artifact.get();
  }

  @Override
  public @NotNull String getArtifactName(@NotNull ArtifactModel artifactModel) {
    Artifact artifact = getArtifactNoResolve();
    if (artifact != null) {
      return artifactModel.getArtifactByOriginal(artifact).getName();
    }
    return myName;
  }

  @Override
  public Artifact findArtifact(@NotNull ArtifactModel artifactModel) {
    Artifact artifact = getArtifactNoResolve();
    if (artifact != null) {
      return artifactModel.getArtifactByOriginal(artifact);
    }
    return artifactModel.findArtifact(myName);
  }

  void setArtifact(@NotNull Artifact artifact) {
    myArtifact = new AtomicReference<>(artifact);
  }

  void invalidateArtifact() {
    myArtifact = null;
  }

  void setName(@NotNull String name) {
    myName = name;
  }
}
