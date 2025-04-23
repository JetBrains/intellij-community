// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.List;

/**
 * A representation of the main dependency storage
 */
public interface DependencyGraph extends Graph, Closeable {

  Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources, boolean isSourceOnly);

  default DifferentiateResult differentiate(Delta delta, DifferentiateParameters params) {
    return differentiate(delta, params, List.of());
  }

  DifferentiateResult differentiate(Delta delta, DifferentiateParameters params, Iterable<Graph> extParts);

  /**
   * Merge data from the Delta into this dependency storage
   */
  void integrate (@NotNull DifferentiateResult diffResult);
}
