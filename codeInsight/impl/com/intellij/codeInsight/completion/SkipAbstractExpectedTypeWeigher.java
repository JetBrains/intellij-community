/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.statistics.StatisticsManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SkipAbstractExpectedTypeWeigher extends CompletionWeigher {

  enum Result {
    NON_DEFAULT,
    STRING,
    ABSTRACT,
    ACCEPT
  }

  public Comparable weigh(@NotNull final LookupElement<?> item, final CompletionLocation location) {
    return getSkippingStatus(item, location);
  }

  public static Result getSkippingStatus(final LookupElement<?> item, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART) return Result.ACCEPT;

    final Object object = item.getObject();
    if (!(object instanceof PsiClass)) return Result.ACCEPT;

    if (StatisticsManager.getInstance().getUseCount(CompletionRegistrar.STATISTICS_KEY, item, location) > 1) return Result.ACCEPT;

    PsiClass psiClass = (PsiClass)object;

    int toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(psiClass).size();
    if (toImplement > 2) return Result.ABSTRACT;

    for (final PsiMethod method : psiClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        toImplement++;
        if (toImplement > 2) return Result.ABSTRACT;
      }
    }

    final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    boolean isDefaultType = false;
    if (infos != null) {
      final PsiType type = JavaPsiFacade.getInstance(psiClass.getProject()).getElementFactory().createType(psiClass);
      for (final ExpectedTypeInfo info : infos) {
        final PsiType infoType = TypeConversionUtil.erasure(info.getType().getDeepComponentType());
        final PsiType defaultType = TypeConversionUtil.erasure(info.getDefaultType().getDeepComponentType());
        if (!defaultType.equals(infoType) && infoType.isAssignableFrom(type)) {
          if (!defaultType.isAssignableFrom(type)) return Result.NON_DEFAULT;
          isDefaultType = true;
        }
      }
    }

    if (toImplement > 0) return Result.ACCEPT;

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) return Result.ABSTRACT;
    if (!isDefaultType && CommonClassNames.JAVA_LANG_STRING.equals(psiClass.getQualifiedName())) return Result.STRING;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return Result.NON_DEFAULT;

    return Result.ACCEPT;
  }

}
