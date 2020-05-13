// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This class is intentionally package local.
 */
abstract class Queries {

  @NotNull
  static Queries getInstance() {
    return ServiceManager.getService(Queries.class);
  }

  @NotNull
  protected abstract <I, O> Query<O> mapping(@NotNull Query<? extends I> base,
                                             @NotNull Function<? super I, ? extends O> mapper);

  @NotNull
  protected abstract <T> Query<T> filtering(@NotNull Query<T> base,
                                            @NotNull Predicate<? super T> predicate);

  @NotNull
  protected abstract <I, O> Query<O> flatMapping(@NotNull Query<? extends I> base,
                                                 @NotNull Function<? super I, ? extends Query<? extends O>> mapper);
}
