// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

public interface DifferentiateStrategy {
  boolean differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter, Iterable<Node<?, ?>> nodesWithErrors);

  default boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    return true;
  }
}
