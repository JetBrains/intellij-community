// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;

public class EmptyQuery<R> implements Query<R> {
  private static final EmptyQuery<?> EMPTY_QUERY_INSTANCE = new EmptyQuery<>();

  @Override
  public @NotNull @Unmodifiable Collection<R> findAll() {
    return Collections.emptyList();
  }

  @Override
  public R findFirst() {
    return null;
  }

  @Override
  public boolean forEach(@NotNull Processor<? super R> consumer) {
    return true;
  }

  public static <T> Query<T> getEmptyQuery() {
    @SuppressWarnings("unchecked") Query<T> instance = (Query<T>)EMPTY_QUERY_INSTANCE;
    return instance;
  }
}
