// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public abstract class StorageProvider<S extends StorageOwner> {
  /**
   * @deprecated Use {@link #createStorage(Path)}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public @NotNull S createStorage(@NotNull File targetDataDir) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #createStorage(Path, PathRelativizerService)}
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public @NotNull S createStorage(@NotNull File targetDataDir, PathRelativizerService relativizer) throws IOException {
    return createStorage(targetDataDir.toPath());
  }

  public @NotNull S createStorage(@NotNull Path targetDataDir) throws IOException {
    return createStorage(targetDataDir.toFile());
  }

  public @NotNull S createStorage(@NotNull Path targetDataDir, PathRelativizerService relativizer) throws IOException {
    return createStorage(targetDataDir.toFile(), relativizer);
  }
}
