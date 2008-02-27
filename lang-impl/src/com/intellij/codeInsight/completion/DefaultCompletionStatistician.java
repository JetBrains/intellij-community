/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.StatisticsInfo;

/**
 * @author peter
 */
public class DefaultCompletionStatistician extends CompletionStatistician{

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    return new StatisticsInfo("completion", element.getLookupString());
  }
}