// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.memory;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.IndexStorageLockingBase;
import com.intellij.util.indexing.impl.IndexStorageUtil;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
public final class InMemoryIndexStorage<K, V> extends IndexStorageLockingBase implements VfsAwareIndexStorage<K, V> {
  private final Map<K, ValueContainerImpl<V>> inMemoryStorage;

  private volatile boolean closed = false;

  public InMemoryIndexStorage(@NotNull KeyDescriptor<K> keyDescriptor) {
    inMemoryStorage = ConcurrentCollectionFactory.createConcurrentMap(IndexStorageUtil.adaptKeyDescriptorToStrategy(keyDescriptor));
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor,
                             @NotNull GlobalSearchScope scope,
                             @Nullable IdFilter idFilter) {
    return withReadLock(() -> ContainerUtil.and(inMemoryStorage.keySet(), processor::process));
  }

  @Override
  public void addValue(K k, int inputId, V v) {
    withWriteLock(
      () -> inMemoryStorage.computeIfAbsent(k, __ -> ValueContainerImpl.createNewValueContainer()).addValue(inputId, v)
    );
  }

  @Override
  public void removeAllValues(@NotNull K k, int inputId) {
    withWriteLock(() -> {
      ValueContainerImpl<V> container = inMemoryStorage.get(k);
      if (container == null) return;
      container.removeAssociatedValue(inputId);
      if (container.size() == 0) {
        inMemoryStorage.remove(k);
      }
    });
  }

  @Override
  public void updateValue(K key, int inputId, V newValue) throws StorageException {
    withWriteLock(() -> {
      removeAllValues(key, inputId);
      addValue(key, inputId, newValue);
    });
  }

  @Override
  public void clear() {
    inMemoryStorage.clear();
  }

  @Override
  public <E extends Exception> boolean read(K key,
                                            @NotNull ValueContainerProcessor<V, E> processor) throws StorageException, E {
    try (LockStamp ignored = lockForRead()) {
      ValueContainerImpl<V> container = inMemoryStorage.get(key);

      return processor.process(
        Objects.requireNonNullElse(
          container,
          ValueContainer.emptyContainer()
        )
      );
    }
  }

  @Override
  public void clearCaches() {
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  @ApiStatus.Internal
  public boolean isClosed() {
    return closed;
  }

  @Override
  public void flush() {

  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public int keysCountApproximately() {
    return inMemoryStorage.size();
  }
}
