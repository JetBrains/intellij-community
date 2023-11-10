// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.MapletFactory;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.impl.BackDependencyIndexImpl;
import org.jetbrains.jps.javac.Iterators;

import java.io.IOException;
import java.util.Collections;

public final class ClassShortNameIndex extends BackDependencyIndexImpl {
  public static final String NAME = "class-short-names";

  public ClassShortNameIndex(@NotNull MapletFactory cFactory) throws IOException {
    super(NAME, cFactory);
  }

  @Override
  public Iterable<ReferenceID> getIndexedDependencies(@NotNull Node<?, ?> node) {
    if (node instanceof JvmClass) {
      JvmClass cls = (JvmClass)node;
      if (!cls.isAnonymous() && !cls.isLocal()) {
        return Iterators.asIterable(new JvmNodeReferenceID(cls.getShortName()));
      }
    }
    return Collections.emptyList();
  }
}
