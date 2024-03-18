// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import java.util.Collections;

public interface DifferentiateResult {
  default String getSessionName() {
    return "";
  }
  
  Delta getDelta();

  Iterable<Node<?, ?>> getDeletedNodes();

  Iterable<NodeSource> getAffectedSources();

  default boolean isIncremental() {
    return true;
  }

  static DifferentiateResult createNonIncremental(String sessionName, Delta delta, Iterable<Node<?, ?>> deletedNodes) {
    return new DifferentiateResult() {
      @Override
      public String getSessionName() {
        return sessionName;
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
