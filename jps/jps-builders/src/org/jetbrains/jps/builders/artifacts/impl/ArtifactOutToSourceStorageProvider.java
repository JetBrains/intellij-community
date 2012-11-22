package org.jetbrains.jps.builders.artifacts.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.artifacts.ArtifactOutputToSourceMapping;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class ArtifactOutToSourceStorageProvider extends StorageProvider<ArtifactOutputToSourceMapping> {
  public static final ArtifactOutToSourceStorageProvider INSTANCE = new ArtifactOutToSourceStorageProvider();

  private ArtifactOutToSourceStorageProvider() {
  }

  @NotNull
  @Override
  public ArtifactOutputToSourceMapping createStorage(File targetDataDir) throws IOException {
    return new ArtifactOutputToSourceMapping(new File(targetDataDir, "out-src" + File.separator + "data"));
  }
}
