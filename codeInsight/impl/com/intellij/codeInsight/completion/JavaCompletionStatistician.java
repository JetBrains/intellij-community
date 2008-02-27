/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    final Object o = element.getObject();
    if (o instanceof PsiMember) {
      final PsiType qualifierType = JavaCompletionUtil.getQualifierType((LookupItem) element);
      if (qualifierType != null){
        return JavaStatisticsManager.createInfo(qualifierType, (PsiMember)o);
      }
    }
    return null;
  }

}
