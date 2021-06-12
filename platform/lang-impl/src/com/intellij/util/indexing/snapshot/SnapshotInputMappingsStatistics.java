// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.LongAdder;

public final class SnapshotInputMappingsStatistics {
  private final LongAdder totalRequests = new LongAdder();
  private final LongAdder totalMisses = new LongAdder();
  private final ID<?, ?> myIndexId;

  public SnapshotInputMappingsStatistics(@NotNull ID<?, ?> indexId) {
    myIndexId = indexId;
  }

  public @NotNull ID<?, ?> getIndexId() {
    return myIndexId;
  }

  public long getTotalRequests() {
    return totalRequests.longValue();
  }

  public long getTotalMisses() {
    return totalMisses.longValue();
  }

  void update(boolean miss) {
    totalRequests.increment();
    if (miss) {
      totalMisses.increment();
    }
  }

  void reset() {
    totalRequests.reset();
    totalMisses.reset();
  }
}
