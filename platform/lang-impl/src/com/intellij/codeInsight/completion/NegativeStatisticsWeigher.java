/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class NegativeStatisticsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    final StatisticsManager manager = StatisticsManager.getInstance();
    final StatisticsInfo info = StatisticsManager.serialize(CompletionService.STATISTICS_KEY, item, location);
    if (info == null || info == StatisticsInfo.EMPTY) return 0;

    final StatisticsInfo ignoreInfo = new StatisticsInfo(CompletionPreferencePolicy.composeContextWithValue(info), CompletionPreferencePolicy.IGNORED);
    final int count = manager.getUseCount(ignoreInfo);
    if (count >= StatisticsManager.OBLIVION_THRESHOLD - 1) {
      return -1;
    }

    return 0;
  }
}
