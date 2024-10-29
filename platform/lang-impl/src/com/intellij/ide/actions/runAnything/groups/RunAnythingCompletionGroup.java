// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public final class RunAnythingCompletionGroup<V, P extends RunAnythingProvider<V>> extends RunAnythingGroupBase {
  private final @NotNull P myProvider;

  public RunAnythingCompletionGroup(@NotNull P provider) {
    myProvider = provider;
  }

  public @NotNull P getProvider() {
    return myProvider;
  }

  @Override
  public @NotNull String getTitle() {
    return Objects.requireNonNull(getProvider().getCompletionGroupTitle());
  }

  @Override
  public @NotNull Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext, @NotNull String pattern) {
    P provider = getProvider();
    return ContainerUtil.map(provider.getValues(dataContext, pattern), value -> provider.getMainListItem(dataContext, value));
  }

  @Override
  protected @Nullable Matcher getMatcher(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getProvider().getMatcher(dataContext, pattern);
  }

  public static Collection<RunAnythingGroup> createCompletionGroups() {
    return StreamEx.of(RunAnythingProvider.EP_NAME.getExtensions())
                   .map(provider -> createCompletionGroup(provider))
                   .filter(Objects::nonNull)
                   .distinct(group -> group.getTitle())
                   .collect(Collectors.toList());
  }

  public static @Nullable RunAnythingGroup createCompletionGroup(@NotNull RunAnythingProvider provider) {
    String title = provider.getCompletionGroupTitle();
    if (title == null) {
      return null;
    }

    if (RunAnythingGeneralGroup.getGroupTitle().equals(title)) {
      return new RunAnythingGeneralGroup();
    }

    //noinspection unchecked
    return new RunAnythingCompletionGroup(provider);
  }
}