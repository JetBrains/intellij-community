// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public ArtifactOutputToSourceMapping createStorage(File targetDataDir) throws IOException {
    throw new UnsupportedOperationException("Unsupported creation type of ArtifactOutputToSourceMapping");
  }

  @NotNull
  @Override
  public ArtifactOutputToSourceMapping createStorage(File targetDataDir, PathRelativizerService relativizer) throws IOException {
    return new ArtifactOutputToSourceMapping(new File(targetDataDir, "out-src" + File.separator + "data"), relativizer);
  }
}
