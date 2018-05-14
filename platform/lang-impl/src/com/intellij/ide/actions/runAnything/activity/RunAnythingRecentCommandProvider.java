// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public class RunAnythingRecentCommandProvider extends RunAnythingCommandExecutionProviderBase
  implements RunAnythingRecentProvider<String>, RunAnythingMultiParametrizedExecutionProvider<String> {

  @NotNull
  @Override
  public RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull String value) {
    return new RunAnythingItemBase(value, getIcon(value));
  }

  @NotNull
  @Override
  public Collection<String> getValues(@NotNull DataContext dataContext) {
    return RunAnythingCache.getInstance(fetchProject(dataContext)).getState().getCommands();
  }
}