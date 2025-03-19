// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.util.Collections;

public interface DifferentiateResult {
  default String getSessionName() {
    return "";
  }

  DifferentiateParameters getParameters();

  Delta getDelta();

  Iterable<Node<?, ?>> getDeletedNodes();

  Iterable<NodeSource> getAffectedSources();

  default boolean isIncremental() {
    return true;
  }

  static DifferentiateResult createNonIncremental(String sessionName, DifferentiateParameters params, Delta delta, Iterable<Node<?, ?>> deletedNodes) {
    return new DifferentiateResult() {
      @Override
      public String getSessionName() {
        return sessionName;
      }

      @Override
      public DifferentiateParameters getParameters() {
        return params;
      }

      @Override
      public boolean isIncremental() {
        return false;
      }

      @Override
      public Delta getDelta() {
        return delta;
      }

      @Override
      public Iterable<Node<?, ?>> getDeletedNodes() {
        return deletedNodes;
      }

      @Override
      public Iterable<NodeSource> getAffectedSources() {
        return Collections.emptyList();
      }
    };
  }
}
