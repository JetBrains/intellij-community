// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class provides ability to run an arbitrary activity for matched input text.
 * <p>
 * {@link RunAnythingProvider} operates with {@code V} that represents a value to be executed.
 * E.g. {@code V} can be a run configuration, an action or a string command to be executed in console.
 * <p>
 * See {@link RunAnythingRunConfigurationProvider}, {@link RunAnythingCommandProvider} and others inheritors.
 */
public interface RunAnythingProvider<V> {
  ExtensionPointName<RunAnythingProvider> EP_NAME = ExtensionPointName.create("com.intellij.runAnything.executionProvider");

  /**
   * Finds matching value by input {@code pattern}.
   * <p>
   * E.g. if input "open #projectName" an action {@link com.intellij.openapi.actionSystem.AnAction} "Open recent project #projectName" is returned,
   * for "ruby test.rb" an existing run configuration with the name "ruby test.rb" is find.
   *
   * @param dataContext use it to fetch project, module, working directory
   * @param pattern     input string
   */
  @Nullable
  V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Gets completions variants for input command prefix. E.g. "rvm use" provider should return list of sdk versions.
   *
   * @param dataContext use it to fetch project, module, working directory
   */
  @NotNull
  Collection<V> getValues(@NotNull DataContext dataContext);

  /**
   * Execute actual matched {@link #findMatchingValue(DataContext, String)} value.
   *
   * @param dataContext use it to fetch project, module, working directory
   * @param value       matched value
   */
  void execute(@NotNull DataContext dataContext, @NotNull V value);

  /**
   * A value specific icon is painted it in the search field and used by value presentation wrapper.
   * E.g. for a configuration value it gets configuration type icon.
   *
   * @param value matching value
   */
  @Nullable
  Icon getIcon(@NotNull V value);

  /**
   * If select a value in the list this command will be inserted into the search field.
   *
   * @param value matching value
   */
  @NotNull
  String getCommand(@NotNull V value);

  /**
   * Returns text that is painted on the popup bottom and changed according to the list selection.
   */
  @Nullable
  String getAdText();

  /**
   * Returns value's presentation wrapper that is actually added into the main list.
   * See also {@link #getHelpItem(DataContext)}
   *
   * @param dataContext use it to fetch project, module, working directory
   * @param value       matching value
   */
  @NotNull
  RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull V value);

  /**
   * Returns value's presentation wrapper that is actually added into the help list.
   * See also {@link #getMainListItem(DataContext, Object)}
   *
   * @param dataContext use it to fetch project, module, working directory
   */

  @Nullable
  RunAnythingItem getHelpItem(@NotNull DataContext dataContext);

  /**
   * Returns completion group title. {@code null} means that current provider doesn't provide completion.
   */
  @Nullable
  String getCompletionGroupTitle();

  /**
   * Finds provider that matches {@code pattern}
   *
   * @param dataContext use it to fetch project, module, working directory
   * @param pattern     input string
   */
  @Nullable
  static RunAnythingProvider findMatchedProvider(@NotNull DataContext dataContext, @NotNull String pattern) {
    return Arrays.stream(EP_NAME.getExtensions())
                 .filter(provider -> provider.findMatchingValue(dataContext, pattern) != null)
                 .findFirst()
                 .orElse(null);
  }
}