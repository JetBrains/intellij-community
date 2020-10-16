// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

/**
 * This class is intentionally package local.
 */
abstract class Queries {

  @NotNull
  static Queries getInstance() {
    return ApplicationManager.getApplication().getService(Queries.class);
  }

  @NotNull
  protected abstract <I, O> Query<O> transforming(@NotNull Query<? extends I> base,
                                                  @NotNull Function<? super I, ? extends Collection<? extends O>> transformation);

  @NotNull
  protected abstract <I, O> Query<O> flatMapping(@NotNull Query<? extends I> base,
                                                 @NotNull Function<? super I, ? extends Query<? extends O>> mapper);
}
