// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts;

import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

// This class must not be implemented or overridden except the current implementations
// The actual data about the artifacts is stored in the com.intellij.platform.backend.workspace.WorkspaceModel
//   while the implementations of this interface act like a bridge to support the existing API
@ApiStatus.NonExtendable
public interface ArtifactModel {
  @RequiresReadLock
  Artifact @NotNull [] getArtifacts();

  @Nullable
  @RequiresReadLock
  Artifact findArtifact(@NotNull String name);

  @NotNull
  Artifact getArtifactByOriginal(@NotNull Artifact artifact);

  @NotNull
  Artifact getOriginalArtifact(@NotNull Artifact artifact);

  @NotNull
  @RequiresReadLock
  Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type);

  @RequiresReadLock
  List<? extends Artifact> getAllArtifactsIncludingInvalid();
}
