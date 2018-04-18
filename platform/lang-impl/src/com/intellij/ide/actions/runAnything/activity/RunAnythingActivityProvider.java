// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * This class provides ability to run an arbitrary activity for matched 'Run Anything' input text
 */
public interface RunAnythingActivityProvider {
  ExtensionPointName<RunAnythingActivityProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.runAnythingActivityProvider");

  /**
   * If {@code pattern} is matched than an arbitrary activity {@link #runActivity(DataContext, String)} will be executed
   *
   * @param dataContext 'Run Anything' action {@code dataContext}, may retrieve {@link Project} and {@link Module} from here
   * @param pattern     'Run Anything' search bar input text
   * @return true if current provider wants to execute an activity for {@code pattern}
   */
  boolean isMatching(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Executes arbitrary activity in IDE if {@code pattern} is matched as {@link #isMatching(DataContext, String)}
   *
   * @param dataContext 'Run Anything' action {@code dataContext}, may retrieve {@link Project} and {@link Module} from here
   * @param pattern     'Run Anything' search bar input text
   * @return true if succeed, false is failed
   */
  boolean runActivity(@NotNull DataContext dataContext, @NotNull String pattern);

  /**
   * Finds provider that matches {@code pattern}
   *
   * @param dataContext 'Run Anything' action {@code dataContext}, may retrieve {@link Project} and {@link Module} from here
   * @param pattern     'Run Anything' search bar input text
   */
  @Nullable
  static RunAnythingActivityProvider findMatchedProvider(@NotNull DataContext dataContext, @NotNull String pattern) {
    return Arrays.stream(EP_NAME.getExtensions()).filter(provider -> provider.isMatching(dataContext, pattern)).findFirst().orElse(null);
  }
}