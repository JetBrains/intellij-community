// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionProviderGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGeneralGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.items.RunAnythingHelpItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * This class provides ability to run an arbitrary activity for matched 'Run Anything' input text
 */
public abstract class RunAnythingProviderBase<V> implements RunAnythingProvider<V> {
  @NotNull
  public Collection<V> getValues(@NotNull DataContext dataContext) {
    return ContainerUtil.emptyList();
  }

  public boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern, @NotNull V value) {
    return StringUtil.equals(pattern, getCommand(value));
  }

  @Nullable
  public V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern) {
    return getValues(dataContext).stream().filter(value -> isMatching(dataContext, pattern, value)).findFirst().orElse(null);
  }

  @Nullable
  public Icon getIcon(@NotNull V value) {
    return EmptyIcon.ICON_16;
  }

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

  /**
   * Null means no completion
   *
   * @return
   */
  @Nullable
  public String getCompletionGroupTitle() {
    return null;
  }

  @Nullable
  public String getId() {
    return getCompletionGroupTitle();
  }

  /**
   * Null means no completion
   *
   * @return
   */
  @Nullable
  public RunAnythingGroup createCompletionGroup() {
    if (RunAnythingGeneralGroup.GENERAL_GROUP_TITLE.equals(getCompletionGroupTitle())) {
      return RunAnythingGeneralGroup.INSTANCE;
    }

    if (getCompletionGroupTitle() == null) {
      return null;
    }

    return new RunAnythingCompletionProviderGroup<>(this);
  }

  @Nullable
  public Icon getHelpIcon() {
    return EmptyIcon.ICON_16;
  }

  @Nullable
  public String getHelpDescription() {
    return null;
  }

  @Nullable
  public String getHelpCommandPlaceholder() {
    return getHelpCommand();
  }

  /**
   * Null means no help command
   *
   * @return
   */
  @Nullable
  public String getHelpCommand() {
    return null;
  }
}