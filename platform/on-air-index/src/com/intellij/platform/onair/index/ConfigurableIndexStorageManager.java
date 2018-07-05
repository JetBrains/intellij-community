// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.index;

import com.intellij.util.indexing.IndexStorageManager;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;
import com.intellij.util.indexing.*;

import java.io.File;
import java.io.IOException;

public class ConfigurableIndexStorageManager implements IndexStorageManager {

  private final IndexStorageManager delegate;

  public ConfigurableIndexStorageManager() {
    delegate = Boolean.getBoolean("onair.index.experimental") ? new BTreeIndexStorageManager() : new PHMIndexStorageManager();
  }

  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId,
                                                                 DataExternalizer<V> valueExternalizer) throws IOException {
    return delegate.createForwardIndexStorage(indexId, valueExternalizer);
  }

  public <V> PersistentMap<Integer, V> createForwardIndexStorage(ID<?, ?> indexId,
                                                                 DataExternalizer<V> valueExternalizer,
                                                                 File storageFile) throws IOException {
    return delegate.createForwardIndexStorage(indexId, valueExternalizer, storageFile);
  }


  public <K, V> VfsAwareIndexStorage<K, V> createIndexStorage(ID<?, ?> indexId,
                                                       KeyDescriptor<K> keyDescriptor,
                                                       DataExternalizer<V> valueExternalizer,
                                                       int cacheSize,
                                                       boolean keyIsUniqueForIndexedFile,
                                                       boolean buildKeyHashToVirtualFileMapping) throws IOException {
    return delegate.createIndexStorage(indexId, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, buildKeyHashToVirtualFileMapping);
  }
}
