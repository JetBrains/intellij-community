// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface RunAnythingMultiParametrizedExecutionProvider<V> extends RunAnythingParametrizedExecutionProvider<V> {
  @NotNull
  Collection<V> getValues(@NotNull DataContext dataContext);

  @Override
  default boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern) {
    return findMatchingValue(dataContext, pattern) != null;
  }

  @Override
  default void execute(@NotNull DataContext dataContext, @NotNull String pattern) {
    V value = findMatchingValue(dataContext, pattern);
    if (value != null) {
      execute(dataContext, value);
    }
  }

  @Nullable
  default V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getValues(dataContext).stream().filter(value -> isMatching(dataContext, pattern, value)).findFirst().orElse(null);
  }
}