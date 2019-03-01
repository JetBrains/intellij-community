// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.indexing.impl.AbstractMapProviderForwardIndexAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class MapForwardIndexAccessorImpl<Key, Value, Input> extends AbstractMapProviderForwardIndexAccessor<Key, Value, Map<Key, Value>, Input> {
  public MapForwardIndexAccessorImpl(IndexExtension<Key, Value, Input> extension) {
    super(new MapDataExternalizer<>(extension));
  }

  @Nullable
  @Override
  protected Map<Key, Value> convertToDataType(@Nullable Map<Key, Value> map, @Nullable Input input) {
    return map;
  }

  @Nullable
  @Override
  public Map<Key, Value> getMapFromData(@Nullable Map<Key, Value> map) {
    return map;
  }
}
