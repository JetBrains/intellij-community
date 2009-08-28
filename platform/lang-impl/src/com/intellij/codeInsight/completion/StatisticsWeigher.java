/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class StatisticsWeigher extends CompletionWeigher {

  public Comparable weigh(@NotNull final LookupElement item, final CompletionLocation location) {
    return StatisticsManager.getInstance().getUseCount(CompletionService.STATISTICS_KEY, item, location);
  }
}
