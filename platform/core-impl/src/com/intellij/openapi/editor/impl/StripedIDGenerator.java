// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-contention counter.
 * Repeated calls to {@link #next()} return numbers which are unique across all calling threads, and which are increasing over calls within one thread.
 */
@ApiStatus.Internal
public class StripedIDGenerator {
  private static final int CHUNK_SIZE = 1000;
  private final AtomicLong nextChunkStart = new AtomicLong();
  // must not be static since we might want to have several instances of this class
  @SuppressWarnings("ThreadLocalNotStaticFinal")
  private final ThreadLocal<NextPair> localCounter = new ThreadLocal<>();
  private static final class NextPair {
    long nextId;
    final long limit;

    private NextPair(long nextId, long limit) {
      this.nextId = nextId;
      this.limit = limit;
    }
  }

  public long next() {
    NextPair nextPair = localCounter.get();
    if (nextPair == null || nextPair.nextId == nextPair.limit) {
      long start = nextChunkStart.getAndAdd(CHUNK_SIZE);
      nextPair = new NextPair(start, start + CHUNK_SIZE);
      localCounter.set(nextPair);
    }
    long result = nextPair.nextId;
    nextPair.nextId++;
    return result;
  }
}
