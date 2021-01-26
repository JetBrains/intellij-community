// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api;

import com.intellij.util.Query;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

final class NonConfigurableUsageHandler implements UsageHandler<@Nullable Void> {

  private final @NotNull UsageHandler.NonConfigurable myHandler;

  NonConfigurableUsageHandler(@NotNull UsageHandler.NonConfigurable handler) {
    myHandler = handler;
  }

  @Override
  public final @Nullable Void getCustomOptions(@NotNull UsageAction action) {
    return null;
  }

  @Override
  public final boolean hasAnythingToSearch(@Nullable Void customOptions) {
    return false;
  }

  @Override
  public final @Nls(capitalization = Title) @NotNull String getSearchString(@NotNull UsageOptions options, @Nullable Void customOptions) {
    return myHandler.getSearchString(options);
  }

  @Override
  public final @NotNull Query<? extends @NotNull Usage> buildSearchQuery(@NotNull UsageOptions options, @Nullable Void customOptions) {
    return myHandler.buildSearchQuery(options);
  }
}
