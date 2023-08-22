// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RunAnythingCommandExecutionProvider extends RunAnythingCommandProvider {

  @Nullable
  @Override
  public String findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return pattern;
  }

  @Nullable
  @Override
  public String getHelpGroupTitle() {
    return null;
  }
}