// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IntForwardIndexAccessor;
import com.intellij.util.indexing.storage.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class HashIdForwardIndexAccessor<Key, Value, Input>
  extends AbstractMapForwardIndexAccessor<Key, Value, Integer>
  implements IntForwardIndexAccessor<Key, Value> {
  private final UpdatableSnapshotInputMappingIndex<Key, Value, Input> mySnapshotInputMappingIndex;
  private final AbstractMapForwardIndexAccessor<Key, Value, ?> myForwardIndexAccessor;

  HashIdForwardIndexAccessor(@NotNull UpdatableSnapshotInputMappingIndex<Key, Value, Input> snapshotInputMappingIndex,
                             @NotNull AbstractMapForwardIndexAccessor<Key, Value, ?> forwardIndexAccessor) {
    super(EnumeratorIntegerDescriptor.INSTANCE);
    mySnapshotInputMappingIndex = snapshotInputMappingIndex;
    myForwardIndexAccessor = forwardIndexAccessor;
  }

  @Nullable
  @Override
  protected Map<Key, Value> convertToMap(int inputId, @Nullable Integer hashId) throws IOException {
    return hashId == null ? null : mySnapshotInputMappingIndex.readData(hashId);
  }

  @NotNull
  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilderFromInt(int inputId, int hashId) throws IOException {
    Map<Key, Value> map = ContainerUtil.notNullize(convertToMap(inputId, hashId));
    return createDiffBuilderByMap(inputId, map);
  }

  @Override
  public @NotNull InputDataDiffBuilder<Key, Value> createDiffBuilderByMap(int inputId, @Nullable Map<Key, Value> map) throws IOException {
    return myForwardIndexAccessor.createDiffBuilderByMap(inputId, map);
  }

  @Override
  public int serializeIndexedDataToInt(@NotNull InputData<Key, Value> data) {
    return data == InputData.empty() ? 0 : ((HashedInputData<Key, Value>)data).getHashId();
  }

  @Nullable
  @Override
  public Integer convertToDataType(@NotNull InputData<Key, Value> data) {
    return serializeIndexedDataToInt(data);
  }
}
