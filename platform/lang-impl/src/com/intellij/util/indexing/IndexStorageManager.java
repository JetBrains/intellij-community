// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;

import java.io.IOException;

public interface IndexStorageManager {
  static IndexStorageManager getInstance() {
    return ServiceManager.getService(IndexStorageManager.class);
  }

  <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId,
                                                          DataExternalizer<V> valueExternalizer) throws IOException;

  <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                       KeyDescriptor<K> keyDescriptor,
                                                       DataExternalizer<V> valueExternalizer,
                                                       int cacheSize,
                                                       boolean keyIsUniqueForIndexedFile,
                                                       boolean buildKeyHashToVirtualFileMapping) throws IOException;
}
