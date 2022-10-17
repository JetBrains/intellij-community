// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public interface UsageHandler {

  /**
   * @return search string to be shown in the Usage View and/or Usage Popup,
   * e.g. <i>Usages of Method 'foo' of Class 'X'</i>
   */
  @Nls(capitalization = Title) @NotNull String getSearchString(@NotNull UsageOptions options);

  /**
   * @return a usage handler without custom search query,
   * meaning the search is run only with {@link UsageSearchParameters}.
   */
  static @NotNull UsageHandler createEmptyUsageHandler(@NotNull String targetName) {
    return new EmptyUsageHandler(targetName);
  }
}
