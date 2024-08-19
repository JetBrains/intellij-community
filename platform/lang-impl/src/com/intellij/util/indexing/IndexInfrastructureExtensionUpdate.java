// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Indicates is index update provided by {@link FileBasedIndexInfrastructureExtension}
 * or calculated from {@link FileContent} by {@link FileBasedIndexExtension}
 */
@ApiStatus.Internal
public final class IndexInfrastructureExtensionUpdate implements StorageUpdate {
  private final StorageUpdate storageUpdate;
  private final boolean isIndexProvided;

  public IndexInfrastructureExtensionUpdate(@NotNull StorageUpdate update,
                                            boolean isIndexProvided) {
    this.storageUpdate = update;
    this.isIndexProvided = isIndexProvided;
  }

  boolean isIndexProvided() {
    return isIndexProvided;
  }

  @Override
  public boolean update() {
    return storageUpdate.update();
  }
}
