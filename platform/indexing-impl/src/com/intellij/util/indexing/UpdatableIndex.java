// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;

public interface UpdatableIndex<Key, Value, Input, FileCachedData> extends InvertedIndex<Key, Value, Input> {

  boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) throws
                                                                                                                                   StorageException;

  @NotNull
  ReadWriteLock getLock();

  @NotNull
  Map<Key, Value> getIndexedFileData(int fileId) throws StorageException;

  /**
   * Goal of {@link UpdatableIndex#instantiateFileData()} and {@link UpdatableIndex#writeData(Object, IndexedFile)} is to allow
   * saving important data to a cache to use later without read lock in analog of {@link UpdatableIndex#setIndexedStateForFile(int, IndexedFile)}
   */
  @NotNull FileCachedData instantiateFileData();

  void writeData(@NotNull FileCachedData data, @NotNull IndexedFile file);

  void setIndexedStateForFileOnCachedData(int fileId, @NotNull FileCachedData data);

  void setIndexedStateForFile(int fileId, @NotNull IndexedFile file);

  void invalidateIndexedStateForFile(int fileId);

  void setUnindexedStateForFile(int fileId);

  @NotNull
  FileIndexingState getIndexingStateForFile(int fileId, @NotNull IndexedFile file);

  long getModificationStamp();

  void removeTransientDataForFile(int inputId);

  void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Key, Value> diffBuilder);

  @NotNull
  IndexExtension<Key, Value, Input> getExtension();

  void updateWithMap(@NotNull AbstractUpdateData<Key, Value> updateData) throws StorageException;

  void setBufferingEnabled(boolean enabled);

  void cleanupMemoryStorage();

  @TestOnly
  void cleanupForNextTest();

  class EmptyData {
    @SuppressWarnings("InstantiationOfUtilityClass")
    public static final EmptyData INSTANCE = new EmptyData();

    private EmptyData() { }
  }
}
