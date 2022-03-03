// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.OverrideOnly
public interface CustomImplementationFileBasedIndexExtension<K, V> {
  @NotNull
  UpdatableIndex<K, V, FileContent> createIndexImplementation(@NotNull FileBasedIndexExtension<K, V> extension,
                                                              @NotNull VfsAwareIndexStorageLayout<K, V> indexStorageLayout)
    throws StorageException, IOException;

  default void handleInitializationError(@NotNull Throwable e) { }
}