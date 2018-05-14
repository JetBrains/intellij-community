// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.NotNull;

public interface RunAnythingCompletionStringProvider
  extends RunAnythingCompletionProvider<String>, RunAnythingActivityProvider<String> {
  @NotNull
  @Override
  default RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull String value) {
    return new RunAnythingItemBase(getCommand(value), getIcon(value));
  }
}