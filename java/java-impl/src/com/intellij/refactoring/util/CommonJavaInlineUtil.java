// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommonJavaInlineUtil {
  static CommonJavaInlineUtil getInstance() {
    return ApplicationManager.getApplication().getService(CommonJavaInlineUtil.class);
  }


  /**
   * Inlines single occurence of variable
   * @param variable variable to inline
   * @param initializer initializer of variable
   * @param ref reference to inline
   * @param thisAccessExpr qualifier of the reference
   * @return replaced expression
   */
  PsiExpression inlineVariable(@NotNull PsiVariable variable,
                               @NotNull PsiExpression initializer,
                               @NotNull PsiJavaCodeReferenceElement ref,
                               @Nullable PsiExpression thisAccessExpr);
}
