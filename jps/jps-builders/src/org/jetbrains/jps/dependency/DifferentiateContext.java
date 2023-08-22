// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

public interface DifferentiateContext {

  /**
   * Accessor for the main Graph
   */
  Graph getGraph();

  /**
   * Accessor for the delta for which the analysis is done
   */
  Delta getDelta();

  void affectUsage(Usage usage);

  void affectNodeSource(NodeSource source);
}
