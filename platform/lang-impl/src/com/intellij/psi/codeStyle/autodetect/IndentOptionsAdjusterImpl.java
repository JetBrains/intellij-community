// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.autodetect;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

final class IndentOptionsAdjusterImpl implements IndentOptionsAdjuster {
  private static final double RATE_THRESHOLD = 0.8;
  private static final int MAX_INDENT_TO_DETECT = 8;
  
  private final IndentUsageStatistics myStats;

  IndentOptionsAdjusterImpl(IndentUsageStatistics stats) {
    myStats = stats;    
  }

  @Override
  public void adjust(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    boolean isTabsUsed = isTabsUsed(myStats);
    boolean isSpacesUsed = isSpacesUsed(myStats);
    int newIndentSize = isSpacesUsed ? getPositiveIndentSize(myStats) : 0;
    
    if (isTabsUsed) {
      adjustForTabUsage(indentOptions);
    }
    else if (isSpacesUsed) {
      indentOptions.USE_TAB_CHARACTER = false;
      if (newIndentSize > 0 && indentOptions.INDENT_SIZE != newIndentSize) {
        indentOptions.INDENT_SIZE = newIndentSize;
      }
    }
  }

  private static void adjustForTabUsage(@NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    if (indentOptions.USE_TAB_CHARACTER) return;
    
    int continuationRatio = indentOptions.INDENT_SIZE == 0 ? 1 : indentOptions.CONTINUATION_INDENT_SIZE / indentOptions.INDENT_SIZE;
    
    indentOptions.USE_TAB_CHARACTER = true;
    indentOptions.INDENT_SIZE = indentOptions.TAB_SIZE;
    indentOptions.CONTINUATION_INDENT_SIZE = indentOptions.TAB_SIZE * continuationRatio;
  }

  private static boolean isSpacesUsed(IndentUsageStatistics stats) {
    int spaces = stats.getTotalLinesWithLeadingSpaces();
    int total = stats.getTotalLinesWithLeadingSpaces() + stats.getTotalLinesWithLeadingTabs();
    return (double)spaces / total > RATE_THRESHOLD;
  }

  private static boolean isTabsUsed(IndentUsageStatistics stats) {
    return stats.getTotalLinesWithLeadingTabs() > stats.getTotalLinesWithLeadingSpaces();
  }
  
  private static int getPositiveIndentSize(@NotNull IndentUsageStatistics stats) {
    int totalIndentSizesDetected = stats.getTotalIndentSizesDetected();
    if (totalIndentSizesDetected == 0) return -1;

    IndentUsageInfo maxUsedIndentInfo = stats.getKMostUsedIndentInfo(0);
    int maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();

    if (maxUsedIndentSize == 0) {
      if (totalIndentSizesDetected < 2) return -1;

      maxUsedIndentInfo = stats.getKMostUsedIndentInfo(1);
      maxUsedIndentSize = maxUsedIndentInfo.getIndentSize();
    }

    if (maxUsedIndentSize <= MAX_INDENT_TO_DETECT) {
      double usageRate = (double)maxUsedIndentInfo.getTimesUsed() / stats.getTotalLinesWithLeadingSpaces();
      if (usageRate > RATE_THRESHOLD) {
        return maxUsedIndentSize;
      }
    }

    return -1;
  }
}
