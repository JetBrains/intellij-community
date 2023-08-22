// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public final class RunAnythingRecentCommandProvider extends RunAnythingCommandProvider {
  @NotNull
  @Override
  public Collection<String> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RunAnythingCache.getInstance(fetchProject(dataContext)).getState().getCommands();
  }

  @Nullable
  @Override
  public String getHelpGroupTitle() {
    return null;
  }
}