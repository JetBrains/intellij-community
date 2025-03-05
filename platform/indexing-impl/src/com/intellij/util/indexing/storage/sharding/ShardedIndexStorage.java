// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.IndexStorage;
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
                             @NotNull GlobalSearchScope scope,
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
    //MAYBE RC: about exception processing here: it may be useful to catch the exceptions from individual shards, and
    //  continue processing other shards, and only rethrow all the collected exceptions at the end. This way we clean
    //  as many shards as it is possible -- but at the cost of much more complicated code (see IOUtils.closeAllSafely()
    //  for an example of how much space exception processing could take).
    //  The benefit of clearing all the shards except for the failing ones -- is not clear now: btw, this .clear() still
    //  fails, and the overall (sharded) storage state is still messy after that, so that the pluses? Currently I see none,
    //  but benefits may arise if/when we'll develop shards into more self-contained entities with capacity to move/rebuild
    //  individual shards -- in this scenario it may be beneficial to clean 'ok' shards, and, say, rebuild failing ones afterwards
    //  (Same is true for .flush() method)
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
