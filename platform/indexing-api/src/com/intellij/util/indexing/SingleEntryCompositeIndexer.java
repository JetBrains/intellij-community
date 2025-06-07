// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@Internal
@OverrideOnly
public abstract class SingleEntryCompositeIndexer<V, SubIndexerType, SubIndexerVersion> extends SingleEntryIndexer<V> implements CompositeDataIndexer<Integer, V, SubIndexerType, SubIndexerVersion> {
  protected SingleEntryCompositeIndexer(boolean acceptNullValues) {
    super(acceptNullValues);
  }

  @Override
  public final @NotNull Map<Integer, V> map(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType) {
    throw new AssertionError();
  }

  @Override
  protected @Nullable V computeValue(@NotNull FileContent inputData) {
    SubIndexerType subIndexerType = calculateSubIndexer(inputData);
    return subIndexerType == null ? null : computeValue(inputData, Objects.requireNonNull(subIndexerType));
  }

  protected abstract @Nullable V computeValue(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType);
}
