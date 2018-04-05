// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public interface ModelReference {

  @NotNull
  Iterable<? extends ModelResolveResult> resolve(boolean incomplete);

  default boolean references(@NotNull ModelElement target) {
    return ContainerUtil.or(resolve(false), it -> it.getResolvedElement().equals(target));
  }
}
