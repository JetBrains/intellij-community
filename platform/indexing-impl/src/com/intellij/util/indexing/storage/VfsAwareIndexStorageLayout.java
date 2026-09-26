// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.util.indexing.impl.IndexStorageLayout;
import org.jetbrains.annotations.ApiStatus;

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * A main interface to provide custom file-based index implementation. See {@link IndexStorageLayout} for details.
 */
@ApiStatus.Internal
public interface VfsAwareIndexStorageLayout<Key, Value> extends IndexStorageLayout<Key, Value> {
  /**
   * How to behave if {@link #clearIndexData()} is called while index storages (opened by {@link #openForwardIndex()}
   * {@link #openIndexStorage()}) are still opened?
   * true: only warn, false: throw an {@link IllegalStateException}
   */
  boolean WARN_IF_CLEANING_UNCLOSED_STORAGE = getBooleanProperty("indexing.only-warn-if-cleaning-unclosed-storage", true);

  //TODO RC: define a covariant return VfsAwareIndexStorage<Key,Value> openIndexStorage() is useful, because logically
  //         every VfsAwareIndexStorageLayout impl _should_ return VfsAwareIndexStorage impl from that method. Such
  //         an override reduces the need of class-cast also. But currently it is treated as API-breaking override, so
  //         I postpone this refactoring

  void clearIndexData();
}
