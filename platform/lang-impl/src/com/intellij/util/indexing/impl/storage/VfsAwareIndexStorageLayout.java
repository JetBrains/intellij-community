// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage;

import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.IndexStorageLayout;
import com.intellij.util.indexing.impl.forward.*;
import com.intellij.util.indexing.memory.InMemoryForwardIndex;
import com.intellij.util.indexing.memory.InMemoryIndexStorage;
import com.intellij.util.indexing.snapshot.SnapshotInputMappings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public interface VfsAwareIndexStorageLayout<Key, Value> extends IndexStorageLayout<Key, Value> {
  default @Nullable SnapshotInputMappings<Key, Value> getSnapshotInputMappings() throws IOException {
    return null;
  }

  @NotNull
  static <Key, Value> VfsAwareIndexStorageLayout<Key, Value> getLayout(@NotNull FileBasedIndexExtension<Key, Value> indexExtension)
    throws IOException {
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return new InMemoryStorageLayout<>(indexExtension);
    }
    if (indexExtension instanceof SingleEntryFileBasedIndexExtension) {
      return new SingleEntryStorageLayout<>(indexExtension);
    }
    if (VfsAwareMapReduceIndex.hasSnapshotMapping(indexExtension)) {
      return new SnapshotMappingsStorageLayout<>(indexExtension);
    }

    return new DefaultStorageLayout<>(indexExtension);
  }

  @ApiStatus.Internal
  @NotNull
  private static <Key, Value> AbstractMapForwardIndexAccessor<Key, Value, ?> getForwardIndexAccessor(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    if (!(indexExtension instanceof SingleEntryFileBasedIndexExtension) || FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(indexExtension));
    }
    //noinspection unchecked,rawtypes
    return new SingleEntryIndexForwardIndexAccessor(indexExtension);
  }

  @NotNull
  private static <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(FileBasedIndexExtension<K, V> extension) throws IOException {
    return new VfsAwareMapIndexStorage<>(
      IndexInfrastructure.getStorageFile(extension.getName()).toPath(),
      extension.getKeyDescriptor(),
      extension.getValueExternalizer(),
      extension.getCacheSize(),
      extension.keyIsUniqueForIndexedFile(),
      extension.traceKeyHashToVirtualFileMapping()
    );
  }

  final class DefaultStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    @NotNull
    private final FileBasedIndexExtension<K, V> myExtension;

    public DefaultStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension) {
      myExtension = extension;
    }

    @Override
    public @NotNull IndexStorage<K, V> getIndexStorage() throws IOException {
      return VfsAwareIndexStorageLayout.createIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex getForwardIndex() throws IOException {
      File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(myExtension.getName());
      return new PersistentMapBasedForwardIndex(indexStorageFile.toPath(), false, false);
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(myExtension));
    }
  }

  final class SnapshotMappingsStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    private final SnapshotInputMappings<K, V> mySnapshotInputMappings;
    private final FileBasedIndexExtension<K, V> myExtension;

    SnapshotMappingsStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension) throws IOException {
      mySnapshotInputMappings = new SnapshotInputMappings<>(extension, VfsAwareIndexStorageLayout.getForwardIndexAccessor(extension));
      myExtension = extension;
    }

    @Override
    public @NotNull SnapshotInputMappings<K, V> getSnapshotInputMappings() {
      return mySnapshotInputMappings;
    }

    @Override
    public @NotNull IndexStorage<K, V> getIndexStorage() throws IOException {
      return VfsAwareIndexStorageLayout.createIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex getForwardIndex() throws IOException {
      return new IntMapForwardIndex(mySnapshotInputMappings.getInputIndexStorageFile(), true);
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return mySnapshotInputMappings.getForwardIndexAccessor();
    }
  }

  final class SingleEntryStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    @NotNull
    private final FileBasedIndexExtension<K, V> myExtension;

    SingleEntryStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension) {
      myExtension = extension;
    }

    @Override
    public @Nullable SnapshotInputMappings<K, V> getSnapshotInputMappings() {
      return null;
    }

    @Override
    public @NotNull IndexStorage<K, V> getIndexStorage() throws IOException {
      return VfsAwareIndexStorageLayout.createIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex getForwardIndex() {
      return new EmptyForwardIndex();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return new SingleEntryIndexForwardIndexAccessor(myExtension);
    }
  }

  final class InMemoryStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    @NotNull
    private final FileBasedIndexExtension<K, V> myExtension;

    public InMemoryStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension) {
      myExtension = extension;
    }

    @Override
    public @NotNull IndexStorage<K, V> getIndexStorage() {
      return new InMemoryIndexStorage<>(myExtension.getKeyDescriptor());
    }

    @Override
    public @NotNull ForwardIndex getForwardIndex() {
      return new InMemoryForwardIndex();
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(myExtension));
    }
  }
}
