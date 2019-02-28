// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.impl.AbstractForwardIndexAccessor;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapInputDataDiffBuilder;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class MapForwardIndexAccessorImpl<Key, Value, Input> extends AbstractForwardIndexAccessor<Key, Value, Map<Key, Value>, Input> {
  public MapForwardIndexAccessorImpl(IndexExtension<Key, Value, Input> extension) {
    super(new MapDataExternalizer<>(extension));
  }

  @Nullable
  @Override
  protected Map<Key, Value> convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input input) {
    return map;
  }

  @Override
  protected Collection<Key> getKeysFromData(@Nullable Map<Key, Value> map) {
    return map == null ? null : map.keySet();
  }

  @Override
  public InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable ByteArraySequence data, @Nullable Input input)
    throws IOException {
    return new MapInputDataDiffBuilder<>(inputId, getData(data));
  }
}
