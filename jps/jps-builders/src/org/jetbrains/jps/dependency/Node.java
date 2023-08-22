// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.DiffCapable;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.Set;

public interface Node<T extends Node<T, D>, D extends Difference> extends DiffCapable<T, D>, SerializableGraphElement {

  @NotNull
  ReferenceID getReferenceID();

  Iterable<Usage> getUsages();

  default boolean containsAny(Set<Usage> usages) {
    if (!usages.isEmpty()) {
      for (Usage usage : getUsages()) {
        if (usages.contains(usage)) {
          return true;
        }
      }
    }
    return false;
  }
}
