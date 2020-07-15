// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;

public final class JavaMatchers {
  public static PsiMatcherExpression isConstructor(final boolean shouldBe) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        return element instanceof PsiMethod && ((PsiMethod)element).isConstructor() == shouldBe;
      }
    };
  }

  public static PsiMatcherExpression hasModifier(@PsiModifier.ModifierConstant final String modifier, final boolean shouldHave) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        PsiModifierListOwner owner = element instanceof PsiModifierListOwner ? (PsiModifierListOwner) element : null;

        if (owner != null && owner.hasModifierProperty(modifier) == shouldHave) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }
}
