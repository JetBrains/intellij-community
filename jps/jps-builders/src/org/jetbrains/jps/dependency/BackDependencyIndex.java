// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

public interface BackDependencyIndex {
  @NotNull String getName();

  Iterable<ReferenceID> getDependencies(@NotNull ReferenceID id);

  void indexNode(@NotNull Node<?, ?> node);

  void integrate(Iterable<Node<?, ?>> deletedNodes, Iterable<Node<?, ?>> updatedNodes, Iterable<Pair<ReferenceID, Iterable<ReferenceID>>> delta);
}
