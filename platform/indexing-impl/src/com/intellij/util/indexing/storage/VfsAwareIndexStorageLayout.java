// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.storage;

import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.impl.IndexStorageLayout;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * A main interface to provide custom file-based index implementation. See {@link IndexStorageLayout} for details.
 */
@ApiStatus.Internal
public interface VfsAwareIndexStorageLayout<Key, Value> extends IndexStorageLayout<Key, Value> {
  default @Nullable SnapshotInputMappingIndex<Key, Value, FileContent> createOrClearSnapshotInputMappings() throws IOException {
    return null;
  }

  void clearIndexData();
}
