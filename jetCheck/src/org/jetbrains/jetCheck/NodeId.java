// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jetCheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
class NodeId {
  private final AtomicInteger counter;
  final int number;
  @Nullable final Generator<?> generator;

  NodeId(@NotNull Generator<?> generator) {
    this(new AtomicInteger(), generator);
  }

  private NodeId(AtomicInteger counter, @Nullable Generator<?> generator) {
    this.counter = counter;
    this.generator = generator;
    number = counter.getAndIncrement();
  }

  NodeId childId(@Nullable Generator<?> generator) {
    return new NodeId(counter, generator);
  }

  @Override
  public String toString() {
    return String.valueOf(number);
  }
}
