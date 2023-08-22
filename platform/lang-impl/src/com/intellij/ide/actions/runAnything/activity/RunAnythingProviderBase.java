// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  @Override
  public Collection<V> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    return ContainerUtil.emptyList();
  }

  @Override
  @Nullable
  public V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getValues(dataContext, pattern).stream().filter(value -> StringUtil.equals(pattern, getCommand(value))).findFirst().orElse(null);
  }

  @Override
  @Nullable
  public Icon getIcon(@NotNull V value) {
    return null;
  }

  @Override
  @Nullable
  public String getAdText() {
    return null;
  }

  @NotNull
  @Override
  public RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull V value) {
    return new RunAnythingItemBase(getCommand(value), getIcon(value));
  }

  @Nullable
  @Override
  public RunAnythingHelpItem getHelpItem(@NotNull DataContext dataContext) {
    String placeholder = getHelpCommandPlaceholder();
    String commandPrefix = getHelpCommand();
    if (placeholder == null || commandPrefix == null) {
      return null;
    }
    return new RunAnythingHelpItem(placeholder, commandPrefix, getHelpDescription(), getHelpIcon());
  }

  @Override
  @Nullable
  public String getCompletionGroupTitle() {
    return null;
  }

  @Nullable
  @Override
  public Matcher getMatcher(@NotNull DataContext dataContext, @NotNull String pattern) {
    return null;
  }

  @NotNull
  @Override
  public List<RunAnythingContext> getExecutionContexts(@NotNull DataContext dataContext) {
    return RunAnythingChooseContextAction.Companion.allContexts(RunAnythingUtil.fetchProject(dataContext));
  }

  @Nullable
  public Icon getHelpIcon() {
    return EmptyIcon.ICON_16;
  }

  @Nullable
  @Nls
  public @NlsContexts.DetailedDescription String getHelpDescription() {
    return null;
  }

  @Nullable
  public @NlsSafe String getHelpCommandPlaceholder() {
    return getHelpCommand();
  }

  /**
   * Null means no help command
   *
   */
  @Nullable
  public @NlsSafe String getHelpCommand() {
    return null;
  }
}