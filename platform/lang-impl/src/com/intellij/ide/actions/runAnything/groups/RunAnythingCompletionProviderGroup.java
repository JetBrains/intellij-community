// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.ide.actions.runAnything.activity.RunAnythingCompletionProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public abstract class RunAnythingCompletionProviderGroup<V, P extends RunAnythingCompletionProvider<V>> extends RunAnythingGroupBase {
  public static final Collection<RunAnythingGroup> MAIN_GROUPS = getAllGroups();

  @NotNull
  protected abstract P getProvider();

  @NotNull
  @Override
  public String getTitle() {
    return getProvider().getGroupTitle();
  }

  @NotNull
  @Override
  public Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext) {
    P provider = getProvider();
    return provider.getValues(dataContext).stream().map(value -> provider.getMainListItem(dataContext, value)).collect(Collectors.toList());
  }

  public final boolean isVisible(@NotNull DataContext dataContext) {
    return RunAnythingCache.getInstance(fetchProject(dataContext)).isGroupVisible(getProvider().getId());
  }

  public static Collection<RunAnythingGroup> getAllGroups() {
    return StreamEx.of(RunAnythingActivityProvider.EP_NAME.getExtensions())
                   .select(RunAnythingCompletionProvider.class)
                   .map(provider -> provider.createGroup())
                   .collect(Collectors.toList());
  }
}