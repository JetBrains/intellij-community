// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface DifferentiateContext {

  DifferentiateParameters getParams();

  /**
   * Accessor for the main Graph
   */
  @NotNull
  Graph getGraph();

  /**
   * Accessor for the delta for which the analysis is done
   */
  @NotNull
  Delta getDelta();

  void affectUsage(@NotNull Usage usage);

  void affectUsage(@NotNull Usage usage, @NotNull Predicate<Node<?, ?>> constraint);

  void affectUsage(Iterable<? extends ReferenceID> affectionScopeNodes, @NotNull Predicate<Node<?, ?>> usageQuery);

  void affectNodeSource(@NotNull NodeSource source);

  boolean isCompiled(NodeSource src);

  boolean isDeleted(ReferenceID id);
}
