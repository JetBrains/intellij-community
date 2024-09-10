// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;

import java.util.function.Consumer;

public class LoggingGraph implements Graph {

  private final Graph myDelegate;
  private final Consumer<String> myLogger;

  public LoggingGraph(Graph delegate, Consumer<String> logger) {
    myDelegate = delegate;
    myLogger = logger;
  }

  public Graph getDelegate() {
    return myDelegate;
  }

  protected void debug(String message, Object... details) {
    StringBuilder msg = new StringBuilder(message);
    for (Object detail : details) {
      msg.append(detail);
    }
    debug(msg.toString());
  }

  protected void debug(String message) {
    myLogger.accept(message);
  }

  @Override
  public Iterable<BackDependencyIndex> getIndices() {
    return myDelegate.getIndices();
  }

  @Override
  public @Nullable BackDependencyIndex getIndex(String name) {
    return myDelegate.getIndex(name);
  }

  @Override
  public @NotNull Iterable<ReferenceID> getDependingNodes(@NotNull ReferenceID id) {
    return myDelegate.getDependingNodes(id);
  }

  @Override
  public Iterable<NodeSource> getSources(@NotNull ReferenceID id) {
    return myDelegate.getSources(id);
  }

  @Override
  public Iterable<ReferenceID> getRegisteredNodes() {
    return myDelegate.getRegisteredNodes();
  }

  @Override
  public Iterable<NodeSource> getSources() {
    return myDelegate.getSources();
  }

  @Override
  public Iterable<Node<?, ?>> getNodes(@NotNull NodeSource source) {
    return myDelegate.getNodes(source);
  }

  @Override
  public <T extends Node<T, ?>> Iterable<T> getNodes(NodeSource src, Class<T> nodeSelector) {
    return myDelegate.getNodes(src, nodeSelector);
  }
}
