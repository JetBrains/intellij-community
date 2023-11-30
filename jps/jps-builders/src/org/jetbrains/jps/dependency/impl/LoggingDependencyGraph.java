// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.incremental.Utils;

import java.io.IOException;
import java.util.function.Consumer;

public class LoggingDependencyGraph extends LoggingGraph implements DependencyGraph {
  private long myTotalDifferentiateTime;
  private long myTotalIntegrateTime;

  public LoggingDependencyGraph(DependencyGraph delegate, Consumer<String> logger) {
    super(delegate, logger);
  }

  public long getTotalDifferentiateTime() {
    return myTotalDifferentiateTime;
  }

  public long getTotalIntegrateTime() {
    return myTotalIntegrateTime;
  }

  @Override
  public DependencyGraph getDelegate() {
    return (DependencyGraph)super.getDelegate();
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources) throws IOException {
    return getDelegate().createDelta(sourcesToProcess, deletedSources);
  }

  @Override
  public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params) {
    long start = System.currentTimeMillis();
    try {
      return getDelegate().differentiate(delta, params);
    }
    finally {
      long duration = System.currentTimeMillis() - start;
      myTotalDifferentiateTime += duration;
      debug("DependencyGraph differentiate ", params.getSessionName(), " done in ", Utils.formatDuration(duration));
    }
  }

  @Override
  public void integrate(@NotNull DifferentiateResult diffResult) {
    long start = System.currentTimeMillis();
    try {
      getDelegate().integrate(diffResult);
    }
    finally {
      long duration = System.currentTimeMillis() - start;
      myTotalIntegrateTime += duration;
      debug("DependencyGraph integrate ", diffResult.getSessionName(), " done in ", Utils.formatDuration(duration));
    }
  }

  @Override
  public void close() throws IOException {
    try {
      getDelegate().close();
    }
    finally {
      debug("DependencyGraph total differentiate time ", Utils.formatDuration(myTotalDifferentiateTime));
      debug("DependencyGraph total integrate     time ", Utils.formatDuration(myTotalIntegrateTime));
    }
  }
}
