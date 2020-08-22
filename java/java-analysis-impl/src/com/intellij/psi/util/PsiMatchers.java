// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class PsiMatchers {

  private PsiMatchers() {
  }

  @NotNull
  public static PsiMatcherExpression hasName(@NotNull final String name) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element instanceof PsiNamedElement && name.equals(((PsiNamedElement) element).getName())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasText(@NotNull final String text) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (element.getTextLength() != text.length()) return Boolean.FALSE;
        return text.equals(element.getText());
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasText(final String @NotNull ... texts) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        String text = element.getText();
        return ArrayUtil.find(texts, text) != -1;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasClass(@NotNull final Class<?> aClass) {
    return new PsiMatcherExpression() {
      @Override
      public Boolean match(PsiElement element) {
        if (aClass.isAssignableFrom(element.getClass())) return Boolean.TRUE;
        return Boolean.FALSE;
      }
    };
  }

  @NotNull
  public static PsiMatcherExpression hasClass(final Class @NotNull ... classes) {
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
