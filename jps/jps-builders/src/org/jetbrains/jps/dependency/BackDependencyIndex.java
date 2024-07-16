// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public interface BackDependencyIndex {
  @NotNull String getName();

  Iterable<ReferenceID> getKeys();

  Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id);

  void indexNode(@NotNull Node<?, ?> node);

  void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, BackDependencyIndex deltaIndex);

  static BackDependencyIndex createEmpty(String name) {
    return new BackDependencyIndex() {
      @Override
      public @NotNull String getName() {
        return name;
      }

      @Override
      public Iterable<ReferenceID> getKeys() {
        return Collections.emptyList();
      }

      @Override
      public Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id) {
        return Collections.emptyList();
      }

      @Override
      public void indexNode(@NotNull Node<?, ?> node) {
        // nothing here
      }

      @Override
      public void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, BackDependencyIndex deltaIndex) {
        // nothing here
      }
    };
  }
}
