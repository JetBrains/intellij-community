// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;

public final class JavaMatchers {
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
