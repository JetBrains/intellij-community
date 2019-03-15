// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapInputDataDiffBuilder;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

class HashIdForwardIndexAccessor<Key, Value, Input> extends AbstractForwardIndexAccessor<Key, Value, Integer, Input> {
  private final SnapshotInputMappingIndex<Key, Value, Input> mySnapshotInputMappingIndex;

  HashIdForwardIndexAccessor(@NotNull SnapshotInputMappingIndex<Key, Value, Input> snapshotInputMappingIndex) {
    super(EnumeratorIntegerDescriptor.INSTANCE);
    mySnapshotInputMappingIndex = snapshotInputMappingIndex;
  }

  @Override
  protected InputDataDiffBuilder<Key, Value> createDiffBuilder(int inputId, @Nullable Integer hashId) throws IOException {
    return new MapInputDataDiffBuilder<>(inputId, hashId == null ? null : mySnapshotInputMappingIndex.readData(hashId));
  }

  @Override
  public Integer convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input content) {
    try {
      return content == null ? null : mySnapshotInputMappingIndex.getHashId(content);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
