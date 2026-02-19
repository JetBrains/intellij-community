// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;

/**
 * The class is meant to be accessed from synchronized block only
 */
@VisibleForTesting
@ApiStatus.Internal
public final class Timestamps {

  private final Object2LongMap<ID<?, ?>> myIndexStamps;
  private boolean myIsDirty = false;

  public Timestamps() {
    this(new Object2LongOpenHashMap<>(5, 0.98f));
  }

  Timestamps(Object2LongMap<ID<?, ?>> indexStamps) {
    myIndexStamps = indexStamps;
  }

  public @NotNull TimestampsImmutable toImmutable() {
    return new TimestampsImmutable(myIndexStamps);
  }

  @VisibleForTesting
  public long get(ID<?, ?> id) {
    return myIndexStamps != null ? myIndexStamps.getLong(id) : IndexingStamp.HAS_NO_INDEXED_DATA_STAMP;
  }

  @VisibleForTesting
  public void set(ID<?, ?> id, long tmst) {
    if (tmst == IndexingStamp.INDEX_DATA_OUTDATED_STAMP && !myIndexStamps.containsKey(id)) {
      return;
    }

    long previous = tmst == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP ? myIndexStamps.removeLong(id) : myIndexStamps.put(id, tmst);
    if (previous != tmst) {
      myIsDirty = true;
    }
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  @Override
  public String toString() {
    return "Timestamps{" +
           "indexStamps: " + myIndexStamps +
           ", dirty: " + myIsDirty +
           '}';
  }

  boolean hasIndexingTimeStamp() {
    return myIndexStamps != null && !myIndexStamps.isEmpty();
  }

  Collection<? extends ID<?, ?>> getIndexIds() {
    return myIndexStamps.keySet();
  }
}
