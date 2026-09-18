// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProviderKt;
import com.intellij.util.indexing.impl.storage.StorageRef;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapBasedForwardIndex;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage;
import com.intellij.util.indexing.impl.storage.durablemap.LegacyAdapter;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Provides a database-backed storage layout for a regular index. */
final class DurableDatabaseStorageLayout<Key, Value> implements VfsAwareIndexStorageLayout<Key, Value> {
  private final @NotNull IndexId<Key, Value> indexId;

  private final @NotNull DurableDatabase database;

  private final @NotNull MapForwardIndexAccessor<Key, Value> forwardIndexAccessor;

  private final @NotNull StorageAccessor<PatchableDurableMap<Key, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>> valueMapAccessor;
  private final @NotNull StorageAccessor<DurableMap<Integer, ByteArraySequence>> forwardMapAccessor;

  private final @NotNull StorageRef<DurableMapIndexStorage<Key, Value>, IOException> indexStorageRef;
  private final @NotNull StorageRef<DurableMapBasedForwardIndex, IOException> forwardIndexRef;

  DurableDatabaseStorageLayout(@NotNull DurableDatabase database,
                               @NotNull FileBasedIndexExtension<Key, Value> extension) {
    this.database = database;
    indexId = extension.getName();
    DataExternalizer<Map<Key, Value>> inputMapExternalizer = DefaultIndexStorageLayoutProviderKt.defaultMapExternalizerFor(extension);
    forwardIndexAccessor = new MapForwardIndexAccessor<>(inputMapExternalizer);

    KeyDescriptorEx<Key> keyDescriptor = LegacyAdapter.adapt(extension.getKeyDescriptor());
    DataExternalizer<Value> valueExternalizer = extension.getValueExternalizer();
    valueMapAccessor = DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      indexId,
      keyDescriptor,
      valueExternalizer
    );
    forwardMapAccessor = DurableDatabaseMapAccessors.forwardMapAccessor(database, indexId);
    indexStorageRef = new StorageRef<>(
      "IndexStorage[" + extension.getName() + "]",
      () -> new DurableMapIndexStorage<>(
        valueMapAccessor::open,
        keyDescriptor,
        valueExternalizer,
        extension.getCacheSize(),
        extension.keyIsUniqueForIndexedFile()
      ),
      IndexStorage::isClosed,
      /* failIfNotClosed: */ !WARN_IF_CLEANING_UNCLOSED_STORAGE
    );

    forwardIndexRef = new StorageRef<>(
      "ForwardIndex[" + extension.getName() + "]",
      () -> new DurableMapBasedForwardIndex(forwardMapAccessor::open),
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
  @SuppressWarnings("DuplicatedCode")
  public synchronized void clearIndexData() {
    try {
      //we used [value|forward]MapAccessor.cleanData() here, which is a nice abstraction.
      // But for sharded indexes we need to remove (among others) the older shards, which may have nothing to do
      // with current storage and its MapAccessor => cleanIndexData() is used now, and MapAccessor.cleanData()
      // is abandoned
      indexStorageRef.ensureClosedAndRun(() ->
        forwardIndexRef.ensureClosedAndRun(
          () -> DurableDatabaseMapAccessors.cleanIndexData(database, indexId)
        )
      );
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
