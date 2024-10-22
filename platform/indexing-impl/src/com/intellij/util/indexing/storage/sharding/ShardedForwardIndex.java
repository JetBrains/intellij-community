// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Sharded forward index: a wrapper around N {@link PersistentMapBasedForwardIndex} non-sharded forward
 * indexes.
 */
class ShardedForwardIndex implements ForwardIndex {
  private final ShardableIndexExtension extension;
  private final ForwardIndex[] shards;

  private volatile boolean closed = false;

  <Value, Key> ShardedForwardIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull ThrowableNotNullFunction<Integer, ForwardIndex, IOException> forwardIndexesFactory)
    throws IOException {
    if (!(extension instanceof ShardableIndexExtension shardableExtension)) {
      throw new IllegalArgumentException("Extension(" + extension + ") must be ShardableIndexExtension");
    }
    this.extension = shardableExtension;

    shards = new ForwardIndex[shardableExtension.shardsCount()];
    try {
      for (int shardNo = 0; shardNo < shards.length; shardNo++) {
        shards[shardNo] = forwardIndexesFactory.fun(shardNo);
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
  public @Nullable ByteArraySequence get(@NotNull Integer inputId) throws IOException {
    int shardNo = extension.shardNo(inputId);
    return shards[shardNo].get(inputId);
  }

  @Override
  public void put(@NotNull Integer inputId,
                  @Nullable ByteArraySequence value) throws IOException {
    int shardNo = extension.shardNo(inputId);
    shards[shardNo].put(inputId, value);
  }

  @Override
  public void clear() throws IOException {
    for (ForwardIndex shard : shards) {
      shard.clear();
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
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
  public void force() throws IOException {
    for (ForwardIndex shard : shards) {
      shard.force();
    }
  }

  @Override
  public boolean isDirty() {
    for (ForwardIndex shard : shards) {
      if (shard.isDirty()) {
        return true;
      }
    }
    return false;
  }
}
