// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface FileBasedIndexInfrastructureExtensionUpdatableIndex<K, V, I> extends UpdatableIndex<K, V, I> {

  @Override
  @NotNull IndexInfrastructureExtensionUpdateComputation mapInputAndPrepareUpdate(int inputId, @Nullable I content);

  default void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
    setIndexedStateForFile(fileId, file);
  }

  @Override
  default void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
    throw new IllegalStateException("not implemented");
  }
}
