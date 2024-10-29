// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

public final class RunAnythingGeneralGroup extends RunAnythingGroupBase {
  @Override
  public @NotNull String getTitle() {
    return getGroupTitle();
  }

  @Override
  public @NotNull Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext, @NotNull String pattern) {
    Collection<RunAnythingItem> collector = new ArrayList<>();

    for (RunAnythingProvider provider : RunAnythingProvider.EP_NAME.getExtensions()) {
      if (getGroupTitle().equals(provider.getCompletionGroupTitle())) {
        Collection values = provider.getValues(dataContext, pattern);
        for (Object value : values) {
          //noinspection unchecked
          collector.add(provider.getMainListItem(dataContext, value));
        }
      }
    }

    return collector;
  }

  @Override
  protected int getMaxInitialItems() {
    return 15;
  }

  public static @Nls String getGroupTitle() {
    return IdeBundle.message("run.anything.general.group.title");
  }
}
