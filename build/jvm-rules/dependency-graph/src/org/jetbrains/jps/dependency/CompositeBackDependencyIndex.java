// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * A readonly composite backward dependency index view on top of several index parts. Index parts are supposed to be semantically the same.
 */
public interface CompositeBackDependencyIndex extends BackDependencyIndex {

  static CompositeBackDependencyIndex create(String name, Iterable<BackDependencyIndex> parts) {
    for (BackDependencyIndex part : parts) {
      if (!name.equals(part.getName())) {
        throw new RuntimeException("Composite index parts should be of the same kind (must have the same name and be semantically the same)");
      }
    }
    return new CompositeBackDependencyIndex() {
      @Override
      public @NotNull String getName() {
        return name;
      }

      @Override
      public Iterable<ReferenceID> getKeys() {
        return unique(flat(map(parts, BackDependencyIndex::getKeys)));
      }

      @Override
      public Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id) {
        return unique(flat(map(parts, index -> index.getDependencies(id))));
      }

      @Override
      public void indexNode(@NotNull Node<?, ?> node) {
        throw new RuntimeException("Composite index is read-only");
      }

      @Override
      public void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, BackDependencyIndex deltaIndex) {
        throw new RuntimeException("Composite index is read-only");
      }
    };
  }
}
