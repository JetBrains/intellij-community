// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.vfs.newvfs.persistent.ContentHashesUtil;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.IndexInfrastructure;
import com.intellij.util.io.ByteSequenceDataExternalizer;
import com.intellij.util.io.KeyValueStore;
import com.intellij.util.io.PersistentHashMap;

import java.io.File;
import java.io.IOException;

public class JetCacheLocalStorage<K, V> implements KeyValueStore<byte[], ByteArraySequence> {
  private static final Logger LOG = Logger.getInstance(JetCacheLocalStorage.class);
  private final PersistentHashMap<byte[], ByteArraySequence> myPersistentHashMap;
  private final File myFile;

  public JetCacheLocalStorage(FileBasedIndexExtension<K, V> extension) {
    myFile = new File(IndexInfrastructure.getIndexRootDir(extension.getName()), ".jetcache");

    PersistentHashMap<byte[], ByteArraySequence> map = null;
    for (int i = 0; i < 10; i++) {
      try {
        map = new PersistentHashMap<>(myFile, new ContentHashesUtil.ContentHashesDescriptor(), ByteSequenceDataExternalizer.INSTANCE);
      } catch (IOException e) {
        LOG.error(e);
      }
    }
    if (map == null) throw new RuntimeException("Can't initiate JetCacheLocalStorage");
    myPersistentHashMap = map;
  }

  @Override
  public ByteArraySequence get(byte[] key) throws IOException {
    return myPersistentHashMap.get(key);
  }

  @Override
  public void put(byte[] key, ByteArraySequence value) throws IOException {
    myPersistentHashMap.put(key, value);
  }

  @Override
  public void force() {
    myPersistentHashMap.force();
  }

  @Override
  public void close() throws IOException {
    myPersistentHashMap.close();
  }
}
