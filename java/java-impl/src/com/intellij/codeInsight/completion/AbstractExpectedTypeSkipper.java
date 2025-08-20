// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

public final class AbstractExpectedTypeSkipper extends CompletionPreselectSkipper {

  private enum Result {
    NON_DEFAULT,
    STRING,
    ABSTRACT,
    ACCEPT
  }

  @Override
  public boolean skipElement(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    return skips(element, location);
  }

  public static boolean skips(LookupElement element, CompletionLocation location) {
    return getSkippingStatus(element, location) != Result.ACCEPT;
  }

  private static Result getSkippingStatus(final LookupElement item, final CompletionLocation location) {
    if (location.getCompletionType() != CompletionType.SMART && !hasEmptyPrefix(location)) return Result.ACCEPT;
    if (DumbService.getInstance(location.getProject()).isDumb()) return Result.ACCEPT;

    CompletionParameters parameters = location.getCompletionParameters();
    PsiExpression expression = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiExpression.class);
    if (!(expression instanceof PsiNewExpression)) return Result.ACCEPT;

    if (!(item.getObject() instanceof PsiClass psiClass)) return Result.ACCEPT;

    if (StatisticsManager.getInstance().getUseCount(StatisticsWeigher.getBaseStatisticsInfo(item, location)) > 1) return Result.ACCEPT;

    int toImplement = 0;
    for (final PsiMethod method : psiClass.getMethods()) {
      if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        toImplement++;
        if (toImplement > 2) return Result.ABSTRACT;
      }
    }

    toImplement += OverrideImplementExploreUtil.getMapToOverrideImplement(psiClass, true)
                                               .values()
                                               .stream()
                                               .filter(c -> ((PsiMethod)c.getElement()).hasModifierProperty(PsiModifier.ABSTRACT))
                                               .count();
    if (toImplement > 2) return Result.ABSTRACT;

    final ExpectedTypeInfo[] infos = JavaCompletionUtil.EXPECTED_TYPES.getValue(location);
    boolean isDefaultType = false;
    if (infos != null) {
      final PsiType type = JavaPsiFacade.getElementFactory(psiClass.getProject()).createType(psiClass);
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

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (toImplement == 0 && parameters.getCompletionType() == CompletionType.BASIC) return Result.ACCEPT;
      return Result.ABSTRACT;
    }
    if (!isDefaultType && CommonClassNames.JAVA_LANG_STRING.equals(psiClass.getQualifiedName())) return Result.STRING;
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(psiClass.getQualifiedName())) return Result.NON_DEFAULT;

    return Result.ACCEPT;
  }

  private static boolean hasEmptyPrefix(CompletionLocation location) {
    return location.getCompletionParameters().getPosition().getTextRange().getStartOffset() == location.getCompletionParameters().getOffset();
  }
}
