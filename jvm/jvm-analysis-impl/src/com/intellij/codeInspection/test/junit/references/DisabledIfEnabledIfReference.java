// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit.references;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UastContextKt;

final class DisabledIfEnabledIfReference extends BaseJunitAnnotationReference {
  DisabledIfEnabledIfReference(PsiLanguageInjectionHost element) {
    super(element);
  }

  @Override
  protected boolean hasNoStaticProblem(@NotNull PsiMethod method, @NotNull UClass literalClazz, @Nullable UMethod literalMethod) {
    boolean checkIsSuccessful = true;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    UClass uMethodClass = UastContextKt.toUElement(psiClass, UClass.class);
    if (uMethodClass == null) return false;
    final boolean inExternalClazz = !literalClazz.equals(uMethodClass);
    final boolean atClassLevel = literalMethod == null;
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    if ((inExternalClazz || atClassLevel) && !isStatic) checkIsSuccessful = false;
    return checkIsSuccessful && method.getParameterList().isEmpty();
  }
}
