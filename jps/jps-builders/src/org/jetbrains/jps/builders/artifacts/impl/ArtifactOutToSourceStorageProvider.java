// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.artifacts.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;

public final class ArtifactOutToSourceStorageProvider extends StorageProvider<ArtifactOutputToSourceMapping> {
  public static final ArtifactOutToSourceStorageProvider INSTANCE = new ArtifactOutToSourceStorageProvider();

  private ArtifactOutToSourceStorageProvider() {
  }

  @Override
  public @NotNull ArtifactOutputToSourceMapping createStorage(File targetDataDir) {
    throw new UnsupportedOperationException("Unsupported creation type of ArtifactOutputToSourceMapping");
  }

  @Override
  public @NotNull ArtifactOutputToSourceMapping createStorage(File targetDataDir, PathRelativizerService relativizer) throws IOException {
    return new ArtifactOutputToSourceMapping(new File(targetDataDir, "out-src" + File.separator + "data"), relativizer);
  }
}
