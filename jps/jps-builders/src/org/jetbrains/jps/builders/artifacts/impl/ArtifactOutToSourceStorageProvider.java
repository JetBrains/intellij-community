// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.artifacts.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.IOException;
import java.nio.file.Path;

public final class ArtifactOutToSourceStorageProvider extends StorageProvider<ArtifactOutputToSourceMapping> {
  public static final ArtifactOutToSourceStorageProvider INSTANCE = new ArtifactOutToSourceStorageProvider();

  private ArtifactOutToSourceStorageProvider() {
  }

  @Override
  public @NotNull ArtifactOutputToSourceMapping createStorage(@NotNull Path targetDataDir) {
    throw new UnsupportedOperationException("Unsupported creation type of ArtifactOutputToSourceMapping");
  }

  @Override
  public @NotNull ArtifactOutputToSourceMapping createStorage(@NotNull Path targetDataDir, PathRelativizerService relativizer) throws IOException {
    return new ArtifactOutputToSourceMapping(targetDataDir.resolve("out-src/data").toFile(), relativizer);
  }
}
