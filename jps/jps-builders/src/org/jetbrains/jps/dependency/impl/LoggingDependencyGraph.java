// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.incremental.Utils;

import java.io.IOException;
import java.util.function.Consumer;

public final class LoggingDependencyGraph extends LoggingGraph implements DependencyGraph {
  private long myTotalDifferentiateTime;
  private long myTotalIntegrateTime;

  public LoggingDependencyGraph(DependencyGraph delegate, Consumer<String> logger) {
    super(delegate, logger);
  }

  public synchronized long getTotalDifferentiateTime() {
    return myTotalDifferentiateTime;
  }

  public synchronized long getTotalIntegrateTime() {
    return myTotalIntegrateTime;
  }

  @Override
  public DependencyGraph getDelegate() {
    return (DependencyGraph)super.getDelegate();
  }

  @Override
  public Delta createDelta(Iterable<NodeSource> sourcesToProcess, Iterable<NodeSource> deletedSources, boolean isSourceOnly) throws IOException {
    return getDelegate().createDelta(sourcesToProcess, deletedSources, isSourceOnly);
  }

  @Override
  public DifferentiateResult differentiate(Delta delta, DifferentiateParameters params, Iterable<Graph> extParts) {
    long start = System.currentTimeMillis();
    try {
      return getDelegate().differentiate(delta, params, extParts);
    }
    finally {
      long duration = System.currentTimeMillis() - start;
      synchronized (this) {
        myTotalDifferentiateTime += duration;
      }
      debug("DependencyGraph ", params.getSessionName(), " differentiate done in ", Utils.formatDuration(duration));
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
      synchronized (this) {
        myTotalIntegrateTime += duration;
      }
      debug("DependencyGraph ", diffResult.getSessionName(), " integrate done in ", Utils.formatDuration(duration));
    }
  }

  @Override
  public void close() throws IOException {
    try {
      getDelegate().close();
    }
    finally {
      debug("DependencyGraph total differentiate linear time ", Utils.formatDuration(myTotalDifferentiateTime));
      debug("DependencyGraph total integrate     linear time ", Utils.formatDuration(myTotalIntegrateTime));
    }
  }
}
