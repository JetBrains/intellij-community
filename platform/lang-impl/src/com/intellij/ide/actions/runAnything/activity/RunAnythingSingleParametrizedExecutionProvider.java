// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface RunAnythingSingleParametrizedExecutionProvider<V> extends RunAnythingParametrizedExecutionProvider<V> {
  @NotNull
  V getValue(@NotNull DataContext dataContext, @NotNull String pattern);

  @Override
  default boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern) {
    return isMatching(dataContext, pattern, getValue(dataContext, pattern));
  }

  @Override
  default void execute(@NotNull DataContext dataContext, @NotNull String pattern) {
    execute(dataContext, getValue(dataContext, pattern));
  }

  @Nullable
  @Override
  default V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    V value = getValue(dataContext, pattern);
    return isMatching(dataContext, pattern, value) ? value : null;
  }
}
