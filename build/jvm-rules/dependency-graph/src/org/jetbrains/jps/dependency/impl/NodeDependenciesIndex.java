// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.util.Iterators;

public final class NodeDependenciesIndex extends BackDependencyIndexImpl {
  public static final String NAME = "node-backward-dependencies";

  public NodeDependenciesIndex(@NotNull MapletFactory cFactory) {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(Node<?, ?> node) {
    ReferenceID nodeID = node.getReferenceID();
    return Iterators.unique(Iterators.map(Iterators.filter(node.getUsages(), u -> !nodeID.equals(u.getElementOwner())), u -> u.getElementOwner()));
  }
}
