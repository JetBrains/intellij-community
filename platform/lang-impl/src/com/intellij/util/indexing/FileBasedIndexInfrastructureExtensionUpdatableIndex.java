// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileBasedIndexInfrastructureExtensionUpdatableIndex<K, V, I, D> extends UpdatableIndex<K, V, I, D> {

  @Override
  @NotNull IndexInfrastructureExtensionUpdateComputation mapInputAndPrepareUpdate(int inputId, @Nullable I content);

  default void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
    setIndexedStateForFile(fileId, file);
  }

  @Override
  default void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
    throw new IllegalStateException("not implemented");
  }

  default void setIndexedStateForFileOnFileIndexMetaData(int fileId,
                                                         @Nullable D fileIndexMetaData,
                                                         boolean isProvidedByInfrastructureExtension) {
    setIndexedStateForFileOnFileIndexMetaData(fileId, fileIndexMetaData);
  }

  @Override
  default void setIndexedStateForFileOnFileIndexMetaData(int fileId, @Nullable D fileIndexMetaData) {
    throw new IllegalStateException("not implemented");
  }

  UpdatableIndex<K, V, FileContent, D> getBaseIndex();
}
