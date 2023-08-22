// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.artifacts;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ArtifactListener extends EventListener {
  default void artifactAdded(@NotNull Artifact artifact) {
  }

  default void artifactRemoved(@NotNull Artifact artifact) {
  }

  default void artifactChanged(@NotNull Artifact artifact, @NotNull String oldName) {
  }
}
