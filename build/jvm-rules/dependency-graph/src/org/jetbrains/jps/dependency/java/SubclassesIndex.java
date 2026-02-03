// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl;
import org.jetbrains.jps.util.Iterators;

import java.util.Collections;

public final class SubclassesIndex extends BackDependencyIndexImpl {
  public static final String NAME = "direct-subclasses";

  public SubclassesIndex(@NotNull MapletFactory cFactory) {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node) {
    if (!(node instanceof JvmClass)) {
      return Collections.emptyList();
    }
    JvmClass classNode = (JvmClass)node;
    return Iterators.map(classNode.getSuperTypes(), name -> new JvmNodeReferenceID(name));
  }
}
