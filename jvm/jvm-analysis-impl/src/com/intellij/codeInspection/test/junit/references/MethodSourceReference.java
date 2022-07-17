// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit.references;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public final class MethodSourceReference extends BaseJunitAnnotationReference {
  public MethodSourceReference(PsiLanguageInjectionHost element) {
    super(element);
  }

  @Override
  protected boolean hasNoStaticProblem(@NotNull PsiMethod method, @NotNull UClass literalClazz, @Nullable UMethod literalMethod) {
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    return method.getParameterList().isEmpty() && (TestUtils.testInstancePerClass(psiClass) != isStatic);
  }
}
