// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CommonJavaInlineUtilImpl implements CommonJavaInlineUtil {
  @Override
  public PsiExpression inlineVariable(@NotNull PsiVariable variable,
                                      @NotNull PsiExpression initializer,
                                      @NotNull PsiJavaCodeReferenceElement ref,
                                      @Nullable PsiExpression thisAccessExpr) {
    return InlineUtil.inlineVariable(variable, initializer, ref, thisAccessExpr);
  }
}
