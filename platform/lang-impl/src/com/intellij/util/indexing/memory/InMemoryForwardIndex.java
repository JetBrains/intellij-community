// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.memory;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.containers.IntObjectMap;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public final class InMemoryForwardIndex implements ForwardIndex {
  private final IntObjectMap<byte[]> myMap = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  @Override
  public @Nullable ByteArraySequence get(@NotNull Integer key) throws IOException {
    byte[] bytes = myMap.get(key);
    return bytes == null ? null : ByteArraySequence.create(bytes);
  }

  @Override
  public void put(@NotNull Integer key, @Nullable ByteArraySequence value) {
    if (value == null) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value.toBytes());
    }
  }

  @Override
  public void force() { }

  @Override
  public void clear() {
    myMap.clear();
  }

  @Override
  public void close() { }
}
