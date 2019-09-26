// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.Statistician;
import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link Statistician} for code completion, the results are used for sorting and preselection.
 */
public abstract class CompletionStatistician extends Statistician<LookupElement,CompletionLocation> {
  @Override
  public abstract StatisticsInfo serialize(@NotNull final LookupElement element, @NotNull final CompletionLocation location);
}
