/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;

/**
 * @author peter
 */
public class DecoratorCompletionStatistician extends CompletionStatistician{

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    if (element instanceof LookupElementDecorator) {
      return StatisticsManager.serialize(CompletionService.STATISTICS_KEY, ((LookupElementDecorator)element).getDelegate(), location);
    }
    return null;
  }
}