// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api;

import com.intellij.find.FindBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.EmptyQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

final class EmptyUsageHandler implements UsageHandler.NonConfigurable {

  private final @NlsSafe @NotNull String myTargetName;

  EmptyUsageHandler(@NlsSafe @NotNull String targetName) {
    myTargetName = targetName;
  }

  @Nls(capitalization = Title)
  @NotNull
  @Override
  public String getSearchString(@NotNull UsageOptions options) {
    return FindBundle.message("usages.search.title.default", myTargetName);
  }

  @NotNull
  @Override
  public Query<? extends @NotNull Usage> buildSearchQuery(@NotNull UsageOptions options) {
    return EmptyQuery.getEmptyQuery();
  }
}
