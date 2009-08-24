/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class JavaCompletionStatistician extends CompletionStatistician{
  @NonNls public static final String CLASS_NAME_COMPLETION_PREFIX = "classNameCompletion#";

  public StatisticsInfo serialize(final LookupElement element, final CompletionLocation location) {
    final Object o = element.getObject();

    if (o instanceof PsiLocalVariable || o instanceof PsiParameter || o instanceof PsiThisExpression) {
      return StatisticsInfo.EMPTY;
    }

    final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    if (infos != null && infos.length > 0) {
      final boolean primitivesOnly = ContainerUtil.and(infos, new Condition<ExpectedTypeInfo>() {
        public boolean value(final ExpectedTypeInfo info) {
          return info.getType() instanceof PsiPrimitiveType;
        }
      });
      if (primitivesOnly) {
        return StatisticsInfo.EMPTY; //collecting statistics on primitive types usually has only negative effects
      }
    }

    LookupItem item = element.as(LookupItem.class);
    if (item == null) return null;

    PsiType qualifierType = JavaCompletionUtil.getQualifierType(item);
    if (qualifierType == null) {
      if (infos != null && infos.length > 0) {
        qualifierType = infos[0].getDefaultType();
      }
    }

    final CompletionType type = location.getCompletionType();
    if (o instanceof PsiMember) {
      final boolean isClass = o instanceof PsiClass;
      if (qualifierType != null) {
        if (type == CompletionType.SMART) {
          String context;
          if (isClass) {
            context = JavaStatisticsManager.getAfterNewKey(qualifierType);
          } else {
            context = JavaStatisticsManager.getMemberUseKey1(qualifierType);
          }
          return new StatisticsInfo(context, JavaStatisticsManager.getMemberUseKey2((PsiMember)o));
        }
        if (!isClass && type == CompletionType.BASIC) return JavaStatisticsManager.createInfo(qualifierType, (PsiMember)o);
        return StatisticsInfo.EMPTY;
      }

      if (type == CompletionType.CLASS_NAME && isClass) {
        final String qualifiedName = ((PsiClass)o).getQualifiedName();
        if (qualifiedName != null) {
          final String prefixCapitals = StringUtil.capitalsOnly(element.getPrefixMatcher().getPrefix());
          return new StatisticsInfo(CLASS_NAME_COMPLETION_PREFIX + prefixCapitals, qualifiedName);
        }
      }
    }

    if (qualifierType != null) return StatisticsInfo.EMPTY;

    return null;
  }

}
