// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.io.*;

import java.io.File;
import java.io.IOException;

public class PHMIndexStorageManager implements IndexStorageManager {

  @Override
  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId, DataExternalizer<V> valueExternalizer)
    throws IOException {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
      try {
        final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile(indexId);
        return new PersistentHashMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, valueExternalizer);
      } finally {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }

  }

  @Override
  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId, DataExternalizer<V> valueExternalizer, File storageFile)
    throws IOException {
    return new PersistentHashMap<>(storageFile, EnumeratorIntegerDescriptor.INSTANCE, valueExternalizer);
  }

  @Override
  public <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                              KeyDescriptor<K> keyDescriptor,
                                                              DataExternalizer<V> valueExternalizer,
                                                              int cacheSize,
                                                              boolean keyIsUniqueForIndexedFile,
                                                              boolean buildKeyHashToVirtualFileMapping) throws IOException {
    return new VfsAwareMapIndexStorage<>(
          IndexInfrastructure.getStorageFile(indexId),
          keyDescriptor,
          valueExternalizer,
          cacheSize,
          keyIsUniqueForIndexedFile,
          buildKeyHashToVirtualFileMapping);
  }
}
