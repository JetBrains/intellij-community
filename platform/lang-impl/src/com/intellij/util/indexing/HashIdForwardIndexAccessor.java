// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

class HashIdForwardIndexAccessor<Key, Value, Input> extends AbstractMapForwardIndexAccessor<Key, Value, Integer, Input> {
  private final SnapshotInputMappingIndex<Key, Value, Input> mySnapshotInputMappingIndex;

  HashIdForwardIndexAccessor(@NotNull SnapshotInputMappingIndex<Key, Value, Input> snapshotInputMappingIndex) {
    super(EnumeratorIntegerDescriptor.INSTANCE);
    mySnapshotInputMappingIndex = snapshotInputMappingIndex;
  }

  @Nullable
  @Override
  protected Map<Key, Value> convertToMap(@Nullable Integer hashId) throws IOException {
    return hashId == null ? null : mySnapshotInputMappingIndex.readData(hashId);
  }

  @NotNull
  @Override
  public Integer convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input content) {
    try {
      return mySnapshotInputMappingIndex.getHashId(content);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
