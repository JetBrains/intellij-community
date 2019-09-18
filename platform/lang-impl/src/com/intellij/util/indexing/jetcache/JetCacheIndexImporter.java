// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache;

import com.intellij.index.IndexImporterFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class JetCacheIndexImporter implements IndexImporterFactory {
  @Nullable
  @Override
  public <Key, Value, Input> SnapshotInputMappingIndex<Key, Value, Input> createImporter(@NotNull IndexExtension<Key, Value, Input> extension) {
    if (!JetCacheService.IS_ENABLED) return null;

    JetCacheService jetCacheService = JetCacheService.getInstance();
    JetCacheLocalStorage<Key, Value> storage = jetCacheService.getStorage(((FileBasedIndexExtension<Key, Value>)extension).getName());
    if (storage == null) return null;
    return new SnapshotInputMappingIndex<Key, Value, Input>() {
      private final InputMapExternalizer<Key, Value> myMapExternalizer = new InputMapExternalizer<>(extension);

      @Nullable
      @Override
      public InputData<Key, Value> readData(@NotNull Input content) throws IOException {
        ByteArraySequence serializedData = storage.get(calculateHash(((FileContent)content)));
        return serializedData == null ? null : new InputData<>(AbstractForwardIndexAccessor.deserializeFromByteSeq(serializedData, myMapExternalizer));
      }

      @Override
      public void close() throws IOException {
        storage.close();
      }
    };
  }

  private static final Key<byte[]> HASH_KEY = Key.create("file.content.hash");
  @NotNull
  private static byte[] calculateHash(FileContent content) {
    byte[] hash = content.getUserData(HASH_KEY);
    if (hash == null) {
      byte[] fullHash = ((FileContentImpl)content).getHash();
      if (fullHash == null) {
        throw new IllegalStateException();
      }
      hash = ContentHashesSupport.shortenHash(fullHash);
      content.putUserData(HASH_KEY, hash);
    }
    return hash;
  }
}
