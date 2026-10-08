// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public interface VfsAwareIndexStorage<Key, Value> extends IndexStorage<Key, Value>, MeasurableIndexStore {

  /**
   * Both (scope, idFilter) could be used to skip keys that do not belong to scope, and without fileId matched by idFilter.
   * This is optional optimization: not all indexes supports it, and implementation is free to ignore both filters, and just
   * supply all the keys to processor.
   *
   * @see FileBasedIndexExtension#traceKeyHashToVirtualFileMapping()
   */
  boolean processKeys(@NotNull Processor<? super Key> processor,
                      @NotNull GlobalSearchScope scope,
                      @Nullable IdFilter idFilter) throws StorageException;

}
