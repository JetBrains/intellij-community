// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.api;

import com.intellij.find.FindBundle;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

final class EmptyUsageHandler implements UsageHandler {

  private final @NlsSafe @NotNull String myTargetName;

  EmptyUsageHandler(@NlsSafe @NotNull String targetName) {
    myTargetName = targetName;
  }

  @Override
  public @Nls(capitalization = Title) @NotNull String getSearchString(@NotNull UsageOptions options) {
    return FindBundle.message("usages.search.title.default", myTargetName);
  }
}
