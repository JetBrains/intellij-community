// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.Statistician;
import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A {@link Statistician} for code completion, the results are used for sorting and preselection.
 */
public abstract class CompletionStatistician extends Statistician<LookupElement,CompletionLocation> {
  protected static final Function<@NotNull LookupElement, @Nullable StatisticsInfo> EMPTY_SERIALIZER = e -> StatisticsInfo.EMPTY;
  protected static final Function<@NotNull LookupElement, @Nullable StatisticsInfo> NULL_SERIALIZER = e -> null;

  @Override
  public abstract StatisticsInfo serialize(final @NotNull LookupElement element, final @NotNull CompletionLocation location);

  /**
   * @param location location to apply the statistician for
   * @return partially evaluated statistician; can be used for optimization
   */
  public @NotNull Function<@NotNull LookupElement, @Nullable StatisticsInfo> forLocation(final @NotNull CompletionLocation location) {
    return element -> serialize(element, location);
  }
}
