/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    final Object o = element.getObject();
    PsiType qualifierType = JavaCompletionUtil.getQualifierType((LookupItem) element);
    if (qualifierType == null) {
      final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
      if (infos != null && infos.length > 0) {
        qualifierType = infos[0].getDefaultType();
      }
    }

    final CompletionType type = location.getCompletionType();
    if (o instanceof PsiMember) {
      final boolean isClass = o instanceof PsiClass;
      if (qualifierType != null) {
        if (isClass && type == CompletionType.SMART) return JavaStatisticsManager.createInfo(qualifierType, (PsiMember)o);
        if (!isClass && type == CompletionType.BASIC) return JavaStatisticsManager.createInfo(qualifierType, (PsiMember)o);
        return StatisticsInfo.EMPTY;
      }

      if (type == CompletionType.CLASS_NAME && isClass) {
        final String qualifiedName = ((PsiClass)o).getQualifiedName();
        if (qualifiedName != null) {
          return new StatisticsInfo("classNameCompletion#" + PrefixMatchingWeigher.PREFIX_CAPITALS.getValue(location), qualifiedName);
        }
      }
    }

    if (qualifierType != null) return StatisticsInfo.EMPTY;

    return null;
  }

}