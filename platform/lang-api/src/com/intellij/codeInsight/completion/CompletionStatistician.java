/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.statistics.Statistician;
import com.intellij.psi.statistics.StatisticsInfo;

/**
 * @author peter
 */
public abstract class CompletionStatistician extends Statistician<LookupElement,CompletionLocation> {
  public abstract StatisticsInfo serialize(final LookupElement element, final CompletionLocation location);
}
