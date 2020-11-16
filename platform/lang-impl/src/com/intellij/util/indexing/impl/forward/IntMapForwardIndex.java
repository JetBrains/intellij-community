// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.forward;

import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class IntMapForwardIndex implements IntForwardIndex {
  @NotNull
  private final File myStorageFile;
  private final boolean myHasChunks;
  @NotNull
  private volatile PersistentMap<Integer, Integer> myPersistentMap;

  public IntMapForwardIndex(@NotNull File storageFile,
                            boolean hasChunks) throws IOException {
    myStorageFile = storageFile;
    myHasChunks = hasChunks;
    myPersistentMap = createMap(myStorageFile, myHasChunks);
  }

  @NotNull
  private static PersistentMap<Integer, Integer> createMap(@NotNull File storageFile,
                                                           boolean hasChunks) throws IOException {
    return PersistentHashMapBuilder
      .newBuilder(storageFile.toPath(), EnumeratorIntegerDescriptor.INSTANCE, EnumeratorIntegerDescriptor.INSTANCE)
      .inlineValues()
      .hasChunks(hasChunks)
      .build();
  }

  @Override
  public int getInt(@NotNull Integer key) throws IOException {
    return myPersistentMap.get(key);
  }

  @Override
  public void putInt(@NotNull Integer key, int value) throws IOException {
    myPersistentMap.put(key, value);
  }

  @Override
  public void force() {
    myPersistentMap.force();
  }

  @Override
  public void clear() throws IOException {
    myPersistentMap.deleteMap();
    myPersistentMap = createMap(myStorageFile, myHasChunks);
  }

  @Override
  public void close() throws IOException {
    myPersistentMap.close();
  }
}
