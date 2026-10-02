// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 'Run Anything' popup help section is divided into groups by categories.
 * E.g. 'ruby' help group contains 'ruby' related run configuration commands, 'rvm use #sdk_version' commands etc.
 */

public class RunAnythingHelpGroup<P extends RunAnythingProvider> extends RunAnythingGroupBase {
  public static final ExtensionPointName<RunAnythingGroup> EP_NAME = ExtensionPointName.create("com.intellij.runAnything.helpGroup");

  private @NotNull @Nls(capitalization = Nls.Capitalization.Title) String myTitle = "undefined"; //NON-NLS
  private @NotNull List<P> myProviders = ContainerUtil.emptyList();

  public RunAnythingHelpGroup(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String title, @NotNull List<P> providers) {
    myTitle = title;
    myProviders = providers;
  }

  /**
   * @deprecated API compatibility
   */
  @Deprecated(forRemoval = true)
  public RunAnythingHelpGroup() { }

  @Override
  public @NotNull String getTitle() {
    return myTitle;
  }

  /**
   * Returns collections of providers each of them is expecting to provide not null {@link RunAnythingProvider#getHelpItem(DataContext)}
   * See also {@code RunAnythingProviderBase.getHelp*()} methods.
   * @deprecated please use {@link RunAnythingProvider#getHelpGroupTitle()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Collection<P> getProviders() {
    return myProviders;
  }

  @Override
  public @NotNull Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getProviders()
      .stream()
      .map(provider -> provider.getHelpItem(dataContext))
      .filter(Objects::nonNull)
      .sorted(Comparator.comparing(RunAnythingItem::getCommand))
      .collect(Collectors.toList());
  }

  @Override
  protected int getMaxInitialItems() {
    return 15;
  }
}