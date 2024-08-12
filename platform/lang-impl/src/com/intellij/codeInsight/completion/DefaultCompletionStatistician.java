// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public final class DefaultCompletionStatistician extends CompletionStatistician{

  @Override
  public @NotNull Function<@NotNull LookupElement, @Nullable StatisticsInfo> forLocation(@NotNull CompletionLocation location) {
    String context = "completion#" + location.getCompletionParameters().getOriginalFile().getLanguage();
    return element -> new StatisticsInfo(context, element.getLookupString());
  }

  @Override
  public StatisticsInfo serialize(final @NotNull LookupElement element, final @NotNull CompletionLocation location) {
    return forLocation(location).apply(element);
  }
}