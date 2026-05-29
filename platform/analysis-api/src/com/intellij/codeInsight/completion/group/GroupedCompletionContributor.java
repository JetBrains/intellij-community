// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.group;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a contributor for grouped code completion. Implementations of this interface are intended to define specific
 * grouping behavior for completion items in the IDE, allowing completion results to be categorized into separate groups.
 */
@ApiStatus.Internal
public interface GroupedCompletionContributor {
  boolean groupIsEnabled(@NotNull CompletionParameters parameters);

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getGroupDisplayName();

  /**
   * Determines whether the grouped code completion feature is enabled at the application level.
   *
   * @return true if grouped code completion is enabled at the application level, otherwise false.
   */
  static boolean isGroupEnabledInApp() {
    return ApplicationManager.getApplication().getService(GroupedCompletion.class).isEnabled();
  }
}
