// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reliable and efficient reference to (probably non-existing) artifact by its name. If you have a part of a project configuration
 * which refers to an artifact by name, you can store an instance returned by {@link ArtifactPointerManager#createPointer(String)} instead of
 * storing the artifact name. This allows you to get an Artifact instance via {@link #getArtifact()} which is more efficient than
 * {@link ArtifactManager#findArtifact(String)}, and {@link #getArtifactName()}  artifact name} encapsulated inside the instance will be properly
 * updated if the artifact it refers to is renamed.
 */
public interface ArtifactPointer {
  @NotNull @NlsSafe String getArtifactName();

  @Nullable Artifact getArtifact();

  @NotNull @NlsSafe String getArtifactName(@NotNull ArtifactModel artifactModel);

  @Nullable Artifact findArtifact(@NotNull ArtifactModel artifactModel);
}
