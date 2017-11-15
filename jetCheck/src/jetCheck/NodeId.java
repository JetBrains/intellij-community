// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetCheck;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
class NodeId {
  private final AtomicInteger counter;
  final int number;

  NodeId() {
    this(new AtomicInteger());
  }

  private NodeId(AtomicInteger counter) {
    this.counter = counter;
    number = counter.getAndIncrement();
  }

  NodeId childId() {
    return new NodeId(counter);
  }

  @Override
  public String toString() {
    return String.valueOf(number);
  }
}
