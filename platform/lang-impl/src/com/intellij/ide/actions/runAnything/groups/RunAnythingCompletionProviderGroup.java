// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public class RunAnythingCompletionProviderGroup<V, P extends RunAnythingProvider<V>> extends RunAnythingGroupBase {
  public static final Collection<RunAnythingGroup> MAIN_GROUPS = getAllGroups();

  @NotNull private final P myProvider;

  public RunAnythingCompletionProviderGroup(@NotNull P provider) {
    myProvider = provider;
  }

  @NotNull
  protected P getProvider() {
    return myProvider;
  }

  @NotNull
  @Override
  public String getTitle() {
    return ObjectUtils.assertNotNull(getProvider().getCompletionGroupTitle());
  }

  @NotNull
  @Override
  public Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext) {
    P provider = getProvider();
    return provider.getValues(dataContext).stream().map(value -> provider.getMainListItem(dataContext, value)).collect(Collectors.toList());
  }

  public final boolean isVisible(@NotNull DataContext dataContext) {
    return RunAnythingCache.getInstance(fetchProject(dataContext)).isGroupVisible(Objects.requireNonNull(getProvider().getId()));
  }

  public static Collection<RunAnythingGroup> getAllGroups() {
    return StreamEx.of(RunAnythingProvider.EP_NAME.getExtensions())
                   .map(provider -> provider.createCompletionGroup())
                   .filter(Objects::nonNull)
                   .distinct()
                   .collect(Collectors.toList());
  }
}