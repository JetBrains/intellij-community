// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public interface SymbolReference {

  @NotNull
  Iterable<? extends SymbolResolveResult> resolve(boolean incomplete);

  default boolean references(@NotNull Symbol target) {
    return ContainerUtil.or(resolve(false), it -> it.getTarget().equals(target));
  }
}
