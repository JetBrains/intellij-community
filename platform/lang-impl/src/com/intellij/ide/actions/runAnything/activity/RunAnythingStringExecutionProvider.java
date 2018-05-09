// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

public interface RunAnythingStringExecutionProvider extends RunAnythingParametrizedExecutionProvider<RunAnythingStringValue> {
  @NotNull
  @Override
  default String getTextAsParameter(@NotNull RunAnythingStringValue value) {
    return value.getDelegate();
  }

  @Override
  default void execute(@NotNull DataContext dataContext, @NotNull RunAnythingStringValue value) {
    execute(dataContext, value.getDelegate());
  }
}
