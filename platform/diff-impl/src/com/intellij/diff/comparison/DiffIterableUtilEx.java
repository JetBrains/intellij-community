// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DiffIterableUtilEx {
  @NotNull
  public static FairDiffIterable diff(int @NotNull [] data1, int @NotNull [] data2, @NotNull ProgressIndicator indicator)
    throws DiffTooBigException {
    return DiffIterableUtil.diff(data1, data2, new IndicatorCancellationChecker(indicator));
  }

  @NotNull
  public static <T> FairDiffIterable diff(T @NotNull [] data1, T @NotNull [] data2, @NotNull ProgressIndicator indicator)
    throws DiffTooBigException {
    return DiffIterableUtil.diff(data1, data2, new IndicatorCancellationChecker(indicator));
  }

  @NotNull
  public static <T> FairDiffIterable diff(@NotNull List<? extends T> objects1,
                                          @NotNull List<? extends T> objects2,
                                          @NotNull ProgressIndicator indicator)
    throws DiffTooBigException {
    return DiffIterableUtil.diff(objects1, objects2, new IndicatorCancellationChecker(indicator));
  }
}
