// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// Abstracts a particular storage lifecycle operations: opens it and clears it
@ApiStatus.Internal
public interface StorageAccessor<S> {
  @NotNull S open() throws IOException;

  /// Cleans all the data that belongs to the storage; storage must be closed beforehand
  void cleanData() throws IOException;
}
