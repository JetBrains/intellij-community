// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// This class must not be implemented or overridden except the current implementations
// The actual data about the artifacts is stored in the com.intellij.platform.backend.workspace.WorkspaceModel
//   while the implementations of this interface act like a bridge to support the existing API
@ApiStatus.NonExtendable
public interface ModifiableArtifactModel extends ArtifactModel {

  default @NotNull ModifiableArtifact addArtifact(final @NotNull String name, @NotNull ArtifactType artifactType) {
    return addArtifact(name, artifactType, artifactType.createRootElement(name));
  }

  default @NotNull ModifiableArtifact addArtifact(final @NotNull String name, @NotNull ArtifactType artifactType, CompositePackagingElement<?> rootElement) {
    return addArtifact(name, artifactType, rootElement, null);
  }

  @NotNull
  ModifiableArtifact addArtifact(@NotNull String name, @NotNull ArtifactType artifactType, CompositePackagingElement<?> rootElement,
                                 @Nullable ProjectModelExternalSource externalSource);

  void removeArtifact(@NotNull Artifact artifact);

  @NotNull
  ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact);

  @Nullable
  Artifact getModifiableCopy(@NotNull Artifact artifact);

  void addListener(@NotNull ArtifactListener listener);

  void removeListener(@NotNull ArtifactListener listener);


  boolean isModified();

  @RequiresWriteLock
  void commit();

  void dispose();
}
