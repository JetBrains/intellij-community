// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;

/**
 * A representation of the main dependency storage
 */
public interface DependencyGraph extends Graph, Closeable {

  Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources) throws IOException;

  DifferentiateResult differentiate(Delta delta, DifferentiateParameters params);

  /**
   * Merge data from the Delta into this dependency storage
   */
  void integrate (@NotNull DifferentiateResult diffResult);
}
