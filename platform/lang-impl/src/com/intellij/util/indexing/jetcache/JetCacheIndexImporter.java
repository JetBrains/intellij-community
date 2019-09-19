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
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.toHexString;

public class JetCacheIndexImporter implements IndexImporterFactory {
  @Nullable
  @Override
  public <Key, Value, Input> SnapshotInputMappingIndex<Key, Value, Input> createImporter(@NotNull IndexExtension<Key, Value, Input> extension) {
    if (!JetCacheService.getIS_ENABLED()) return null;

    JetCacheService jetCacheService = JetCacheService.getInstance();
    JetCacheLocalStorage<Key, Value> storage = jetCacheService.getStorage(((FileBasedIndexExtension<Key, Value>)extension).getName());
    if (storage == null) return null;
    return new UpdatableSnapshotInputMappingIndex<Key, Value, Input>() {
      @NotNull
      @Override
      public Map<Key, Value> readData(int hashId) throws IOException {
        throw new UnsupportedOperationException();
      }

      @Override
      public InputData<Key, Value> putData(@NotNull Input content, @NotNull InputData<Key, Value> data) throws IOException {
        byte[] mixedHash = ContentHashesSupport
          .calcContentIdHash(calculateHash((FileContent)content), ((FileBasedIndexExtension<Key, Value>)extension).getName());
        jetCacheService.getJetCache().put(mixedHash, AbstractForwardIndexAccessor.serializeToByteSeq(data.getKeyValues(), myMapExternalizer, 8).toBytes());
        return null;
      }

      @Override
      public void flush() throws IOException {

      }

      @Override
      public void clear() throws IOException {

      }

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
  public static byte[] calculateHash(@NotNull FileContent content) {
    byte[] hash = content.getUserData(HASH_KEY);
    if (hash == null) {
      byte[] fullHash = ((FileContentImpl)content).getHash();
      if (fullHash == null) {
        fullHash = FileBasedIndexImpl
          .calculateHash(content.getContent(), content.getFile().getCharset(),
                         content.getFileType(), content.getFileType());
        ((FileContentImpl)content).setHash(fullHash);
      }
      hash = ContentHashesSupport.shortenHash(fullHash);
      content.putUserData(HASH_KEY, hash);
    }
    return hash;
  }
}
