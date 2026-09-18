// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProviderKt;
import com.intellij.util.indexing.impl.storage.StorageRef;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Actual implementation is mostly in {@link ShardedIndexStorage} and {@link ShardedForwardIndex}, this class just
 * combines them together.
 * <p>
 * Implementation is mostly copied with {@link com.intellij.util.indexing.impl.storage.DefaultIndexStorageLayoutProvider.DefaultStorageLayout}
 */
public class ShardedStorageLayout<Key, Value> implements VfsAwareIndexStorageLayout<Key, Value> {
  private final FileBasedIndexExtension<Key, Value> extension;

  private final MapForwardIndexAccessor<Key, Value> forwardIndexAccessor;

  private final StorageRef<ShardedForwardIndex, IOException> forwardIndexRef;
  private final StorageRef<ShardedIndexStorage<Key, Value>, IOException> indexStorageRef;
  private final ThrowableRunnable<? extends IOException> indexStorageCleaner;
  private final ThrowableRunnable<? extends IOException> forwardIndexCleaner;

  public ShardedStorageLayout(@NotNull FileBasedIndexExtension<Key, Value> extension,
                              @NotNull ThrowableNotNullFunction<Integer, ForwardIndex, IOException> forwardIndexFactory,
                              @NotNull ThrowableNotNullFunction<Integer, VfsAwareIndexStorage<Key, Value>, IOException> indexStorageFactory) {
    this(extension, forwardIndexFactory, indexStorageFactory, () -> { }, () -> { });
  }

  public ShardedStorageLayout(@NotNull FileBasedIndexExtension<Key, Value> extension,
                              @NotNull ThrowableNotNullFunction<Integer, ForwardIndex, IOException> forwardIndexFactory,
                              @NotNull ThrowableNotNullFunction<Integer, VfsAwareIndexStorage<Key, Value>, IOException> indexStorageFactory,
                              @NotNull ThrowableRunnable<? extends IOException> indexStorageCleaner,
                              @NotNull ThrowableRunnable<? extends IOException> forwardIndexCleaner) {
    if (!(extension instanceof ShardableIndexExtension)) {
      throw new IllegalArgumentException("Extension(" + extension + ") must be ShardableIndexExtension");
    }
    this.extension = extension;
    this.indexStorageCleaner = indexStorageCleaner;
    this.forwardIndexCleaner = forwardIndexCleaner;

    DataExternalizer<Map<Key, Value>> inputMapExternalizer = inputMapExternalizerFor(extension);
    forwardIndexAccessor = new MapForwardIndexAccessor<>(inputMapExternalizer);

    indexStorageRef = new StorageRef<>(
      "IndexStorage[" + extension.getName() + "]",
      () -> new ShardedIndexStorage<>(extension, indexStorageFactory),
      IndexStorage::isClosed,
      /* failIfNotClosed: */ !VfsAwareIndexStorageLayout.WARN_IF_CLEANING_UNCLOSED_STORAGE
    );

    forwardIndexRef = new StorageRef<>(
      "ForwardIndex[" + extension.getName() + "]",
      () -> new ShardedForwardIndex(extension, forwardIndexFactory),
      ForwardIndex::isClosed,
      /* failIfNotClosed: */ !VfsAwareIndexStorageLayout.WARN_IF_CLEANING_UNCLOSED_STORAGE
    );
  }

  private static <Key, Value> @NotNull DataExternalizer<Map<Key, Value>> inputMapExternalizerFor(@NotNull FileBasedIndexExtension<Key, Value> extension) {
    return DefaultIndexStorageLayoutProviderKt.defaultMapExternalizerFor(extension);
  }

  @Override
  public synchronized @NotNull IndexStorage<Key, Value> openIndexStorage() throws IOException {
    return indexStorageRef.reopen();
  }

  @Override
  public synchronized @Nullable ForwardIndex openForwardIndex() throws IOException {
    return forwardIndexRef.reopen();
  }

  @Override
  public @Nullable ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() {
    return forwardIndexAccessor;
  }

  @Override
  public synchronized void clearIndexData() {
    try {
      indexStorageRef.ensureClosedAndRun(indexStorageCleaner);
      forwardIndexRef.ensureClosedAndRun(forwardIndexCleaner);
      FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(extension.getName()));
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
