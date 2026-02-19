// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

@ApiStatus.Internal
public interface SESearcher {
  ProgressIndicator search(@NotNull Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                           @NotNull String pattern);

  ProgressIndicator findMoreItems(@NotNull Map<? extends SearchEverywhereContributor<?>, Collection<SearchEverywhereFoundElementInfo>> alreadyFound,
                                  @NotNull Map<? extends SearchEverywhereContributor<?>, Integer> contributorsAndLimits,
                                  @NotNull String pattern);
}
