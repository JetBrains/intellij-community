// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.MergeRange;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class ByLine {
  public static @NotNull FairDiffIterable compare(@NotNull List<? extends CharSequence> lines1,
                                                  @NotNull List<? extends CharSequence> lines2,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) {
    return ByLineRt.compare(lines1, lines2, policy, new IndicatorCancellationChecker(indicator));
  }

  public static @NotNull List<MergeRange> compare(@NotNull List<? extends CharSequence> lines1,
                                                  @NotNull List<? extends CharSequence> lines2,
                                                  @NotNull List<? extends CharSequence> lines3,
                                                  @NotNull ComparisonPolicy policy,
                                                  @NotNull ProgressIndicator indicator) {
    return ByLineRt.compare(lines1, lines2, lines3, policy, new IndicatorCancellationChecker(indicator));
  }

  public static @NotNull List<MergeRange> merge(@NotNull List<? extends CharSequence> lines1,
                                                @NotNull List<? extends CharSequence> lines2,
                                                @NotNull List<? extends CharSequence> lines3,
                                                @NotNull ComparisonPolicy policy,
                                                @NotNull ProgressIndicator indicator) {
    return ByLineRt.merge(lines1, lines2, lines3, policy, new IndicatorCancellationChecker(indicator));
  }
}
