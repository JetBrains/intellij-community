// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;


public abstract class GraphFactory {

  public static @NotNull GraphFactory getInstance() {
    return ApplicationManager.getApplication().getService(GraphFactory.class);
  }

  /** Returns a {@link NetworkBuilder} for building directed networks. */
  public abstract @NotNull NetworkBuilder<Object, Object> directedNetwork();

  /** Returns a {@link NetworkBuilder} for building undirected networks. */
  public abstract @NotNull NetworkBuilder<Object, Object> undirectedNetwork();

  /**
   * Returns a {@link NetworkBuilder} initialized with all properties queryable from {@code
   * network}.
   *
   * <p>The "queryable" properties are those that are exposed through the {@link Network} interface,
   * such as {@link Network#isDirected()}. Other properties, such as {@link
   * NetworkBuilder#expectedNodeCount(int)}, are not set in the new builder.
   */
  public abstract <N, E> @NotNull NetworkBuilder<N, E> from(@NotNull Network<N, E> network);
}
