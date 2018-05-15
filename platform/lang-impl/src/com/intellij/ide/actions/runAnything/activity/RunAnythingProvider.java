// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

/**
 * This class provides ability to run an arbitrary activity for matched 'Run Anything' input text
 */
public interface RunAnythingProvider<V> {
  ExtensionPointName<RunAnythingProvider> EP_NAME = ExtensionPointName.create("com.intellij.runAnything.executionProvider");

  @Nullable
  V findMatchingValue(@NotNull DataContext dataContext, @NotNull String pattern);

  @NotNull
  Collection<V> getValues(@NotNull DataContext dataContext);

  boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern, @NotNull V value);

  void execute(@NotNull DataContext dataContext, @NotNull V value);

  @Nullable
  Icon getIcon(@NotNull V value);

  @NotNull
  String getCommand(@NotNull V value);

  @Nullable
  String getAdText();

  @NotNull
  RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull V value);

  /**
   * Null means no completion
   *
   * @return
   */
  @Nullable
  String getCompletionGroupTitle();

  @Nullable
  String getId();

  /**
   * Null means no completion
   *
   * @return
   */
  @Nullable
  RunAnythingGroup createCompletionGroup();

  /**
   * Help section
   *
   * @param dataContext
   * @return
   */

  @Nullable
  RunAnythingItem getHelpItem(@NotNull DataContext dataContext);

  @Nullable
  Icon getHelpIcon();

  @Nullable
  String getHelpDescription();

  @Nullable
  String getHelpCommandPlaceholder();

  /**
   * Null means no help command
   *
   * @return
   */
  @Nullable
  String getHelpCommand();

  /**
   * Finds provider that matches {@code pattern}
   *
   * @param dataContext 'Run Anything' action {@code dataContext}, may retrieve {@link Project} and {@link Module} from here
   * @param pattern     'Run Anything' search bar input text
   */
  @Nullable
  static RunAnythingProvider findMatchedProvider(@NotNull DataContext dataContext, @NotNull String pattern) {
    return Arrays.stream(EP_NAME.getExtensions())
                 .filter(provider -> provider.findMatchingValue(dataContext, pattern) != null)
                 .findFirst()
                 .orElse(null);
  }

  static void executeMatched(@NotNull DataContext dataContext, @NotNull String pattern) {
    List<String> commands = RunAnythingCache.getInstance(fetchProject(dataContext)).getState().getCommands();
    for (RunAnythingProvider provider : EP_NAME.getExtensions()) {
      Object value = provider.findMatchingValue(dataContext, pattern);
      if (value != null) {
        //noinspection unchecked
        provider.execute(dataContext, value);
        commands.remove(pattern);
        commands.add(pattern);
        break;
      }
    }
  }
}