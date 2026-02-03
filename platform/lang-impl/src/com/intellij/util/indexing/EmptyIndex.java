// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.UpdateData;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

final class EmptyIndex<Key, Value, Input> implements UpdatableIndex<Key, Value, Input, Void>, MeasurableIndexStore {
  private final IndexExtension<Key, Value, Input> myExtension;

  EmptyIndex(@NotNull IndexExtension<Key, Value, Input> extension) {
    myExtension = extension;
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    return true;
  }

  @Override
  public @NotNull Map<Key, Value> getIndexedFileData(int fileId) {
    return Collections.emptyMap();
  }

  @Override
  public Void getFileIndexMetaData(@NotNull IndexedFile file) {
    return null;
  }

  @Override
  public void setIndexedStateForFileOnFileIndexMetaData(int fileId, @Nullable Void data, boolean isProvidedByInfrastructureExtension) {
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
  }

  @Override
  public void invalidateIndexedStateForFile(int fileId) {
  }

  @Override
  public void setUnindexedStateForFile(int fileId) {
  }

  @Override
  public @NotNull FileIndexingStateWithExplanation getIndexingStateForFile(int fileId, @NotNull IndexedFile file) {
    return FileIndexingStateWithExplanation.upToDate();
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
  public void updateWith(@NotNull UpdateData<Key, Value> updateData) {
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
  public boolean isDirty() {
    return false;
  }

  @Override
  public <E extends Exception> boolean withData(@NotNull Key key,
                                                @NotNull ValueContainerProcessor<Value, E> processor) throws E {
    return processor.process(ValueContainer.emptyContainer());
  }

  @Override
  public @NotNull StorageUpdate mapInputAndPrepareUpdate(int inputId, @Nullable Input content) {
    return prepareUpdate(inputId, InputData.empty());
  }

  @Override
  public @NotNull StorageUpdate prepareUpdate(int inputId, @NotNull InputData<Key, Value> data) {
    return StorageUpdate.NOOP;
  }

  @Override
  public void flush() {
  }

  @Override
  public void clear() {
  }

  @Override
  public void dispose() {
  }

  @Override
  public int keysCountApproximately() {
    return 0;
  }
}
