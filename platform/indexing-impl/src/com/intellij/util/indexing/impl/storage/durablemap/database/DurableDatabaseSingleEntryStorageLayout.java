// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database;

import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.database.DurableDatabase;
import com.intellij.platform.util.io.storages.durablemap.PatchableDurableMap;
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.forward.EmptyForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.SingleEntryIndexForwardIndexAccessor;
import com.intellij.util.indexing.impl.storage.StorageRef;
import com.intellij.util.indexing.impl.storage.durablemap.DurableMapIndexStorage;
import com.intellij.util.indexing.impl.storage.durablemap.LegacyAdapter;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Provides a database-backed storage layout for a single-entry index. */
final class DurableDatabaseSingleEntryStorageLayout<Value> implements VfsAwareIndexStorageLayout<Integer, Value> {
  private final @NotNull DurableDatabase database;
  private final @NotNull SingleEntryFileBasedIndexExtension<Value> extension;

  private final @NotNull SingleEntryIndexForwardIndexAccessor<Value> forwardIndexAccessor;

  private final @NotNull StorageAccessor<PatchableDurableMap<Integer, UpdatableValueContainer<Value>, ChangeTrackingValueContainer<Value>>> valueMapAccessor;

  private final @NotNull StorageRef<DurableMapIndexStorage<Integer, Value>, IOException> indexStorageRef;

  DurableDatabaseSingleEntryStorageLayout(@NotNull DurableDatabase database,
                                          @NotNull SingleEntryFileBasedIndexExtension<Value> extension) {
    this.database = database;
    this.extension = extension;
    forwardIndexAccessor = new SingleEntryIndexForwardIndexAccessor<>(extension);

    KeyDescriptorEx<Integer> keyDescriptor = LegacyAdapter.adapt(extension.getKeyDescriptor());
    DataExternalizer<Value> valueExternalizer = extension.getValueExternalizer();
    valueMapAccessor = DurableDatabaseMapAccessors.valueMapAccessor(
      database,
      extension.getName(),
      keyDescriptor,
      valueExternalizer
    );
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
  }

  @Override
  public synchronized @NotNull VfsAwareIndexStorage<Integer, Value> openIndexStorage() throws IOException {
    return indexStorageRef.reopen();
  }

  @Override
  public @NotNull ForwardIndex openForwardIndex() {
    return EmptyForwardIndex.INSTANCE;
  }

  @Override
  public @NotNull ForwardIndexAccessor<Integer, Value> getForwardIndexAccessor() {
    return forwardIndexAccessor;
  }

  @Override
  public synchronized void clearIndexData() {
    try {
      indexStorageRef.ensureClosedAndRun(
        () -> DurableDatabaseMapAccessors.cleanIndexData(database, extension.getName())
      );
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
