// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.filter;

/**
 * A readonly value-filtered backward dependency index view on top of another back dependency index
 */
public interface ValuesFilteredBackDependencyIndex extends BackDependencyIndex {

  static ValuesFilteredBackDependencyIndex create(BackDependencyIndex delegate, Predicate<? super ReferenceID> nodesFilter) {
    return new ValuesFilteredBackDependencyIndex() {
      @Override
      public @NotNull String getName() {
        return delegate.getName();
      }

      @Override
      public Iterable<ReferenceID> getKeys() {
        return delegate.getKeys();
      }

      @Override
      public Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id) {
        return filter(delegate.getDependencies(id), nodesFilter::test);
      }

      @Override
      public void indexNode(@NotNull Node<?, ?> node) {
        throw new RuntimeException("ValuesFiltered index is read-only");
      }

      @Override
      public void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, BackDependencyIndex deltaIndex) {
        throw new RuntimeException("ValuesFiltered index is read-only");
      }
    };
  }
}
