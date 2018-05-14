// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunAnythingCommandExecutionProvider extends RunAnythingCommandExecutionProviderBase
  implements RunAnythingActivityProvider<RunAnythingStringValue> {

  @Nullable
  @Override
  public RunAnythingStringValue findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RunAnythingStringValue.create(pattern);
  }
}