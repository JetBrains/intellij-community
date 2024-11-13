// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import com.intellij.util.indexing.impl.storage.VfsAwareMapIndexStorage;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Sharded (inverted) index storage implementation: a wrapper around N {@link VfsAwareMapIndexStorage}
 * non-sharded index storages.
 */
class ShardedIndexStorage<K, V> implements VfsAwareIndexStorage<K, V> {
  private final FileBasedIndexExtension<K, V> extension;

  private final VfsAwareIndexStorage<K, V>[] shards;

  private volatile boolean closed = false;

  ShardedIndexStorage(@NotNull FileBasedIndexExtension<K, V> extension,
                      @NotNull ThrowableNotNullFunction<Integer, VfsAwareIndexStorage<K, V>, IOException> storagesFactory)
    throws IOException {
    if (!(extension instanceof ShardableIndexExtension shardableExtension)) {
      throw new IllegalArgumentException("Extension(" + extension + ") must be ShardableIndexExtension");
    }
    this.extension = extension;

    //noinspection unchecked
    shards = new VfsAwareIndexStorage[shardableExtension.shardsCount()];
    try {
      for (int shardNo = 0; shardNo < shards.length; shardNo++) {
        shards[shardNo] = storagesFactory.fun(shardNo);
      }
    }
    catch (Throwable e) {
      try {
        IOUtil.closeAllSafely(shards);
      }
      catch (Throwable closeEx) {
        e.addSuppressed(closeEx);
      }
      throw e;
    }
  }

  @Override
  public void addValue(K key, int inputId, V value) throws StorageException {
    IndexStorage<K, V> shard = shardFor(inputId);
    shard.addValue(key, inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull K key, int inputId) throws StorageException {
    IndexStorage<K, V> shard = shardFor(inputId);
    shard.removeAllValues(key, inputId);
  }

  @Override
  public void updateValue(K key, int inputId, V newValue) throws StorageException {
    IndexStorage<K, V> shard = shardFor(inputId);
    shard.updateValue(key, inputId, newValue);
  }

  private IndexStorage<K, V> shardFor(int inputId) {
    ShardableIndexExtension shardable = (ShardableIndexExtension)extension;
    int shardNo = shardable.shardNo(inputId);
    return shards[shardNo];
  }

  @Override
  public @NotNull ValueContainer<V> read(K key) throws StorageException {
    //it is ineffective, which is one of the reasons why this method is deprecated
    ValueContainerImpl<V> mergedData = ValueContainerImpl.createNewValueContainer();
    for (IndexStorage<K, V> shard : shards) {
      shard.read(
        key,
        shardContainer -> shardContainer.forEach(
          (id, value) -> {
            mergedData.addValue(id, value);
            return true;
          }
        )
      );
    }
    return mergedData;
  }

  @Override
  public <E extends Exception> boolean read(K key,
                                            @NotNull ValueContainerProcessor<V, E> processor) throws StorageException, E {
    for (VfsAwareIndexStorage<K, V> shard : shards) {
      boolean shouldContinue = shard.read(key, processor);
      if (!shouldContinue) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor,
                             GlobalSearchScope scope,
                             @Nullable IdFilter idFilter) throws StorageException {
    for (VfsAwareIndexStorage<K, V> shard : shards) {
      boolean shouldContinue = shard.processKeys(processor, scope, idFilter);
      if (!shouldContinue) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int keysCountApproximately() {
    int count = 0;
    for (VfsAwareIndexStorage<K, V> shard : shards) {
      int shardKeyCount = shard.keysCountApproximately();
      if (shardKeyCount == -1) {
        return -1;
      }
      count += shardKeyCount;
    }
    return count;
  }

  @Override
  public void clear() throws StorageException {
    for (IndexStorage<K, V> shard : shards) {
      shard.clear();
    }
  }

  @Override
  public void clearCaches() {
    for (IndexStorage<K, V> shard : shards) {
      shard.clearCaches();
    }
  }

  @Override
  public void flush() throws IOException {
    for (IndexStorage<K, V> shard : shards) {
      shard.flush();
    }
  }

  @Override
  public boolean isDirty() {
    for (IndexStorage<K, V> shard : shards) {
      if (shard.isDirty()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    this.closed = true;

    IOUtil.closeAllSafely(shards);
  }

  @Override
  public boolean isClosed() {
    return closed;
  }
}
