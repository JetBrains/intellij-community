// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProviderKt;
import com.intellij.util.indexing.impl.storage.StorageRef;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapBasedForwardIndex;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage;
import com.intellij.util.indexing.impl.storage.durablemap.LegacyAdapter;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.indexing.storage.sharding.ShardableIndexExtension;
import com.intellij.util.indexing.storage.sharding.ShardedForwardIndex;
import com.intellij.util.indexing.storage.sharding.ShardedIndexStorage;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Provides a database-backed storage layout for a sharded index. */
@SuppressWarnings("SplitModeApiUsage")
final class DurableDatabaseShardedStorageLayout<Key, Value> implements VfsAwareIndexStorageLayout<Key, Value> {
  private final @NotNull DurableDatabase database;
  private final @NotNull FileBasedIndexExtension<Key, Value> extension;
  private final @NotNull KeyDescriptorEx<Key> keyDescriptor;
  private final @NotNull DataExternalizer<Value> valueExternalizer;
  private final @NotNull MapForwardIndexAccessor<Key, Value> forwardIndexAccessor;
  private final @NotNull StorageRef<ShardedIndexStorage<Key, Value>, IOException> indexStorageRef;
  private final @NotNull StorageRef<ShardedForwardIndex, IOException> forwardIndexRef;

  DurableDatabaseShardedStorageLayout(@NotNull DurableDatabase database,
                                      @NotNull FileBasedIndexExtension<Key, Value> extension) {
    if (!(extension instanceof ShardableIndexExtension)) {
      throw new IllegalArgumentException("Extension(" + extension + ") must be ShardableIndexExtension");
    }
    this.database = database;
    this.extension = extension;
    keyDescriptor = LegacyAdapter.adapt(extension.getKeyDescriptor());
    valueExternalizer = extension.getValueExternalizer();

    DataExternalizer<Map<Key, Value>> inputMapExternalizer = DefaultIndexStorageLayoutProviderKt.defaultMapExternalizerFor(extension);
    forwardIndexAccessor = new MapForwardIndexAccessor<>(inputMapExternalizer);

    ThrowableNotNullFunction<Integer, VfsAwareIndexStorage<Key, Value>, IOException> storageFactory = shardNo -> {
      var mapAccessor = valueMapAccessor(shardNo);
      return new DurableMapIndexStorage<>(
        mapAccessor::open,
        keyDescriptor,
        valueExternalizer,
        extension.getCacheSize(),
        extension.keyIsUniqueForIndexedFile()
      );
    };
    ThrowableNotNullFunction<Integer, ForwardIndex, IOException> forwardFactory = shardNo ->
      new DurableMapBasedForwardIndex(forwardMapAccessor(shardNo)::open);

    indexStorageRef = new StorageRef<>(
      "IndexStorage[" + extension.getName() + "]",
      () -> new ShardedIndexStorage<>(extension, storageFactory),
      IndexStorage::isClosed,
      /* failIfNotClosed: */ !WARN_IF_CLEANING_UNCLOSED_STORAGE
    );
    forwardIndexRef = new StorageRef<>(
      "ForwardIndex[" + extension.getName() + "]",
      () -> new ShardedForwardIndex(extension, forwardFactory),
      ForwardIndex::isClosed,
      /* failIfNotClosed: */ !WARN_IF_CLEANING_UNCLOSED_STORAGE
    );
  }

  @Override
  public synchronized @NotNull VfsAwareIndexStorage<Key, Value> openIndexStorage() throws IOException {
    return indexStorageRef.reopen();
  }

  @Override
  public synchronized @NotNull ForwardIndex openForwardIndex() throws IOException {
    return forwardIndexRef.reopen();
  }

  @Override
  public @NotNull ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() {
    return forwardIndexAccessor;
  }

  @Override
  public synchronized void clearIndexData() {
    try {
      indexStorageRef.ensureClosedAndRun(() ->
        forwardIndexRef.ensureClosedAndRun(() -> {
          DurableDatabaseMapAccessors.cleanIndexData(database, extension.getName());
        })
      );
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private @NotNull StorageAccessor<PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>>
  valueMapAccessor(int shardNo) {
    return DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      extension.getName(),
      shardNo,
      keyDescriptor,
      valueExternalizer
    );
  }

  private @NotNull StorageAccessor<DurableMap<Integer, ByteArraySequence>> forwardMapAccessor(int shardNo) {
    return DurableDatabaseMapAccessors.forwardMapAccessor(
      database,
      extension.getName(),
      shardNo
    );
  }
}
