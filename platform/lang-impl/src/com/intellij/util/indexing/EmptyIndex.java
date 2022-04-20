// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.AbstractUpdateData;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.snapshot.EmptyValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

final class EmptyIndex<Key, Value, Input> implements UpdatableIndex<Key, Value, Input, Object> {
  private final ReadWriteLock myLock = new ReentrantReadWriteLock();
  private final IndexExtension<Key, Value, Input> myExtension;

  EmptyIndex(@NotNull IndexExtension<Key, Value, Input> extension) {
    myExtension = extension;
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return true;
  }

  @Override
  public @NotNull ReadWriteLock getLock() {
    return myLock;
  }

  @Override
  public @NotNull Map<Key, Value> getIndexedFileData(int fileId) {
    return Collections.emptyMap();
  }

  @Override
  public @NotNull Object instantiateFileData() {
    return ObjectUtils.NULL;
  }

  @Override
  public void writeData(@NotNull Object data, @NotNull IndexedFile file) {
  }

  @Override
  public void setIndexedStateForFileOnCachedData(int fileId, @NotNull Object data) {
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
  }

  @Override
  public void invalidateIndexedStateForFile(int fileId) {
  }

  @Override
  public void setUnindexedStateForFile(int fileId) {
  }

  @Override
  public @NotNull FileIndexingState getIndexingStateForFile(int fileId, @NotNull IndexedFile file) {
    return FileIndexingState.UP_TO_DATE;
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
  }

  @Override
  public void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Key, Value> diffBuilder) {
  }

  @Override
  public @NotNull IndexExtension<Key, Value, Input> getExtension() {
    return myExtension;
  }

  @Override
  public void updateWithMap(@NotNull AbstractUpdateData<Key, Value> updateData) {
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
  }

  @Override
  public void cleanupMemoryStorage() {
  }

  @Override
  public void cleanupForNextTest() {
  }

  @Override
  public @NotNull ValueContainer<Value> getData(@NotNull Key key) {
    //noinspection unchecked
    return EmptyValueContainer.INSTANCE;
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable Input content) {
    return prepareUpdate(inputId, InputData.empty());
  }

  @Override
  public @NotNull Computable<Boolean> prepareUpdate(int inputId, @NotNull InputData<Key, Value> data) {
    return () -> {
      return true;
    };
  }

  @Override
  public void flush() {
  }

  @Override
  public void clear() throws StorageException {
  }

  @Override
  public void dispose() {
  }
}
