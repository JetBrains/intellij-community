// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

import org.jetbrains.annotations.NotNull;

public interface GraphConfiguration {

  @NotNull
  NodeSourcePathMapper getPathMapper();

  @NotNull
  DependencyGraph getGraph();

  default boolean isGraphUpdated() {
    return true;
  }
}
