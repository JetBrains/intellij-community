// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ByWord {
  public static @NotNull List<DiffFragment> compare(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull ComparisonPolicy policy,
                                                    @NotNull ProgressIndicator indicator) {
    return ByWordRt.compare(text1, text2, policy, new IndicatorCancellationChecker(indicator));
  }

  public static @NotNull List<ByWordRt.LineBlock> compareAndSplit(@NotNull CharSequence text1,
                                                                  @NotNull CharSequence text2,
                                                                  @NotNull ComparisonPolicy policy,
                                                                  @NotNull ProgressIndicator indicator) {
    return ByWordRt.compareAndSplit(text1, text2, policy, new IndicatorCancellationChecker(indicator));
  }
}
