// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@ApiStatus.Internal
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
  public @Unmodifiable @NotNull Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext, @NotNull String pattern) {
    P provider = getProvider();
    return ContainerUtil.map(provider.getValues(dataContext, pattern), value -> provider.getMainListItem(dataContext, value));
  }

  @Override
  protected @Nullable Matcher getMatcher(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getProvider().getMatcher(dataContext, pattern);
  }

  public static Collection<RunAnythingGroup> createCompletionGroups() {
    Set<String> titles = new HashSet<>();
    List<RunAnythingGroup> groups = new ArrayList<>();
    for (RunAnythingProvider provider : RunAnythingProvider.EP_NAME.getExtensions()) {
      RunAnythingGroup group = createCompletionGroup(provider);
      if (group != null && titles.add(group.getTitle())) {
        groups.add(group);
      }
    }
    return groups;
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