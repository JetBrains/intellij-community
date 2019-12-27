// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class SingleEntryCompositeIndexer<V, SubIndexerType, SubIndexerVersion> extends SingleEntryIndexer<V> implements CompositeDataIndexer<Integer, V, SubIndexerType, SubIndexerVersion> {
  protected SingleEntryCompositeIndexer(boolean acceptNullValues) {
    super(acceptNullValues);
  }

  @NotNull
  @Override
  public final Map<Integer, V> map(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType) {
    throw new AssertionError();
  }

  @Nullable
  @Override
  protected V computeValue(@NotNull FileContent inputData) {
    SubIndexerType subIndexerType = calculateSubIndexer(inputData);
    if (subIndexerType == null && !InvertedIndex.ARE_COMPOSITE_INDEXERS_ENABLED) {
      return null;
    }
    return computeValue(inputData, ObjectUtils.notNull(subIndexerType));
  }

  @Nullable
  protected abstract V computeValue(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType);
}
