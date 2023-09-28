// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;

public abstract class StorageProvider<S extends StorageOwner> {
  public abstract @NotNull S createStorage(File targetDataDir) throws IOException;

  public @NotNull S createStorage(File targetDataDir, PathRelativizerService relativizer) throws IOException {
    return createStorage(targetDataDir);
  }
}
