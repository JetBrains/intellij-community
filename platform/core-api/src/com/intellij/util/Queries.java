// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

@ApiStatus.Internal
public abstract class Queries {
  static @NotNull Queries getInstance() {
    return ApplicationManager.getApplication().getService(Queries.class);
  }

  protected abstract @NotNull <I, O> Query<O> transforming(@NotNull Query<? extends I> base,
                                                           @NotNull Function<? super I, ? extends Collection<? extends O>> transformation);

  protected abstract @NotNull <I, O> Query<O> flatMapping(@NotNull Query<? extends I> base,
                                                          @NotNull Function<? super I, ? extends Query<? extends O>> mapper);
}
