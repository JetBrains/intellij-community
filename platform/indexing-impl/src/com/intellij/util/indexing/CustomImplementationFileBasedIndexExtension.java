// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@Internal
public interface CustomImplementationFileBasedIndexExtension<K, V> {
  @NotNull
  UpdatableIndex<K, V, FileContent, ?> createIndexImplementation(@NotNull FileBasedIndexExtension<K, V> extension,
                                                                 @NotNull VfsAwareIndexStorageLayout<K, V> indexStorageLayout)
    throws StorageException, IOException;

  default void handleInitializationError(@NotNull Throwable e) { }
}