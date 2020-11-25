// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage;

import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.IndexStorageLayout;
import com.intellij.util.indexing.impl.forward.*;
import com.intellij.util.indexing.memory.InMemoryForwardIndex;
import com.intellij.util.indexing.memory.InMemoryIndexStorage;
import com.intellij.util.indexing.snapshot.SnapshotInputMappings;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public interface VfsAwareIndexStorageLayout<Key, Value> extends IndexStorageLayout<Key, Value> {
  default @Nullable SnapshotInputMappings<Key, Value> createOrClearSnapshotInputMappings() throws IOException {
    return null;
  }

  @NotNull
  static <Key, Value> VfsAwareIndexStorageLayout<Key, Value> getLayout(@NotNull FileBasedIndexExtension<Key, Value> indexExtension,
                                                                       boolean contentHashEnumeratorReopen)
    throws IOException {
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return new InMemoryStorageLayout<>(indexExtension);
    }
    if (indexExtension instanceof SingleEntryFileBasedIndexExtension) {
      return new SingleEntryStorageLayout<>(indexExtension);
    }
    if (VfsAwareMapReduceIndex.hasSnapshotMapping(indexExtension)) {
      return new SnapshotMappingsStorageLayout<>(indexExtension, contentHashEnumeratorReopen);
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
  private static <K, V> VfsAwareIndexStorage<K, V> createOrClearIndexStorage(FileBasedIndexExtension<K, V> extension) throws IOException {
    Path storageFile = IndexInfrastructure.getStorageFile(extension.getName()).toPath();
    try {
      return new VfsAwareMapIndexStorage<>(
        storageFile,
        extension.getKeyDescriptor(),
        extension.getValueExternalizer(),
        extension.getCacheSize(),
        extension.keyIsUniqueForIndexedFile(),
        extension.traceKeyHashToVirtualFileMapping()
      );
    } catch (IOException e) {
      IOUtil.deleteAllFilesStartingWith(storageFile);
      throw e;
    }
  }

  final class DefaultStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    @NotNull
    private final FileBasedIndexExtension<K, V> myExtension;

    public DefaultStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension) {
      myExtension = extension;
    }

    @Override
    public @NotNull IndexStorage<K, V> createOrClearIndexStorage() throws IOException {
      return VfsAwareIndexStorageLayout.createOrClearIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex createOrClearForwardIndex() throws IOException {
      File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(myExtension.getName());
      try {
        return new PersistentMapBasedForwardIndex(indexStorageFile.toPath(), false, false);
      }
      catch (IOException e) {
        IOUtil.deleteAllFilesStartingWith(indexStorageFile);
        throw e;
      }
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(myExtension));
    }
  }

  final class SnapshotMappingsStorageLayout<K, V> implements VfsAwareIndexStorageLayout<K, V> {
    private final FileBasedIndexExtension<K, V> myExtension;
    private SnapshotInputMappings<K, V> mySnapshotInputMappings;

    SnapshotMappingsStorageLayout(@NotNull FileBasedIndexExtension<K, V> extension, boolean contentHashEnumeratorReopen) {
      myExtension = extension;
      if (!contentHashEnumeratorReopen) {
        deleteIndexData();
      }
    }

    @NotNull
    private SnapshotInputMappings<K, V> initSnapshotInputMappings(@NotNull FileBasedIndexExtension<K, V> extension) throws IOException {
      if (mySnapshotInputMappings == null) {
        try {
          mySnapshotInputMappings = new SnapshotInputMappings<>(extension, VfsAwareIndexStorageLayout.getForwardIndexAccessor(extension));
        } catch (IOException e) {
          deleteIndexData();
          throw e;
        }
      }
      return mySnapshotInputMappings;
    }

    private void deleteIndexData() {
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getPersistentIndexRootDir(myExtension.getName()));
      IOUtil.deleteAllFilesStartingWith(IndexInfrastructure.getIndexRootDir(myExtension.getName()));
    }

    @Override
    public @NotNull SnapshotInputMappings<K, V> createOrClearSnapshotInputMappings() throws IOException {
      return initSnapshotInputMappings(myExtension);
    }

    @Override
    public @NotNull IndexStorage<K, V> createOrClearIndexStorage() throws IOException {
      initSnapshotInputMappings(myExtension);
      return VfsAwareIndexStorageLayout.createOrClearIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex createOrClearForwardIndex() throws IOException {
      initSnapshotInputMappings(myExtension);
      Path storageFile = mySnapshotInputMappings.getInputIndexStorageFile();
      try {
        return new IntMapForwardIndex(storageFile, true);
      }
      catch (IOException e) {
        IOUtil.deleteAllFilesStartingWith(storageFile);
        throw e;
      }
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() throws IOException {
      initSnapshotInputMappings(myExtension);
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
    public @Nullable SnapshotInputMappings<K, V> createOrClearSnapshotInputMappings() {
      return null;
    }

    @Override
    public @NotNull IndexStorage<K, V> createOrClearIndexStorage() throws IOException {
      return VfsAwareIndexStorageLayout.createOrClearIndexStorage(myExtension);
    }

    @Override
    public @NotNull ForwardIndex createOrClearForwardIndex() {
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
    public @NotNull IndexStorage<K, V> createOrClearIndexStorage() {
      return new InMemoryIndexStorage<>(myExtension.getKeyDescriptor());
    }

    @Override
    public @NotNull ForwardIndex createOrClearForwardIndex() {
      return new InMemoryForwardIndex();
    }

    @Override
    public @NotNull ForwardIndexAccessor<K, V> getForwardIndexAccessor() {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(myExtension));
    }
  }
}
