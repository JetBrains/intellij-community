// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class StatisticsComparable implements Comparable<StatisticsComparable> {
  private final int myScalar;
  private final StatisticsInfo myStatisticsInfo;

  public StatisticsComparable(int scalar, @NotNull StatisticsInfo statisticsInfo) {
    myScalar = scalar;
    myStatisticsInfo = statisticsInfo;
  }

  public int getScalar() {
    return myScalar;
  }

  public @NotNull StatisticsInfo getStatisticsInfo() {
    return myStatisticsInfo;
  }

  @Override
  public String toString() {
    return String.valueOf(myScalar);
  }

  @Override
  public int compareTo(StatisticsComparable o) {
    return Integer.compare(myScalar, o.myScalar);
  }
}
