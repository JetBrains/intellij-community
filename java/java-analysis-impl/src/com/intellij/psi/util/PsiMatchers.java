// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class PsiMatchers {

  private PsiMatchers() {
  }

  public static @NotNull PsiMatcherExpression hasName(final @NotNull String name) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element instanceof PsiNamedElement && name.equals(((PsiNamedElement) element).getName())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static @NotNull PsiMatcherExpression hasText(final @NotNull String text) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element.getTextLength() != text.length()) return Boolean.FALSE;
        return text.equals(element.getText());
      }
    };
  }

  public static @NotNull PsiMatcherExpression hasText(final String @NotNull ... texts) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        String text = element.getText();
        return ArrayUtil.find(texts, text) != -1;
      }
    };
  }

  public static @NotNull PsiMatcherExpression hasClass(final @NotNull Class<?> aClass) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  public static @NotNull PsiMatcherExpression hasClass(final Class<?> @NotNull ... classes) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        for (Class<?> aClass : classes) {
          if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }
    };
  }
}
