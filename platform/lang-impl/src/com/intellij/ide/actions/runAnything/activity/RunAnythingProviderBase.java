// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.RunAnythingChooseContextAction;
import com.intellij.ide.actions.runAnything.RunAnythingContext;
import com.intellij.ide.actions.runAnything.RunAnythingUtil;
import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

/**
 * This class provides ability to run an arbitrary activity for matched 'Run Anything' input text
 */
public abstract class RunAnythingProviderBase<V> implements RunAnythingProvider<V> {
  @Override
  public @NotNull Collection<V> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    return ContainerUtil.emptyList();
  }

  @Override
  public @Nullable V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getValues(dataContext, pattern).stream().filter(value -> StringUtil.equals(pattern, getCommand(value))).findFirst().orElse(null);
  }

  @Override
  public @Nullable Icon getIcon(@NotNull V value) {
    return null;
  }

  @Override
  public @Nullable String getAdText() {
    return null;
  }

  @Override
  public @NotNull RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull V value) {
    return new RunAnythingItemBase(getCommand(value), getIcon(value));
  }

  @Override
  public @Nullable RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    String placeholder = getHelpCommandPlaceholder();
    String commandPrefix = getHelpCommand();
    if (placeholder == null || commandPrefix == null) {
      return null;
    }
    return new RunAnythingHelpItem(placeholder, commandPrefix, getHelpDescription(), getHelpIcon());
  }

  @Override
  public @Nullable String getCompletionGroupTitle() {
    return null;
  }

  @Override
  public @Nullable Matcher getMatcher(@NotNull DataContext dataContext, @NotNull String pattern) {
    return null;
  }

  @Override
  public @NotNull List<RunAnythingContext> getExecutionContexts(@NotNull DataContext dataContext) {
    return RunAnythingChooseContextAction.Companion.allContexts(RunAnythingUtil.fetchProject(dataContext));
  }

  public @Nullable Icon getHelpIcon() {
    return EmptyIcon.ICON_16;
  }

  public @Nullable @Nls @NlsContexts.DetailedDescription String getHelpDescription() {
    return null;
  }

  public @Nullable @NlsSafe String getHelpCommandPlaceholder() {
    return getHelpCommand();
  }

  /**
   * Null means no help command
   *
   */
  public @Nullable @NlsSafe String getHelpCommand() {
    return null;
  }
}