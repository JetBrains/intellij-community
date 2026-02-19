// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;


public final class MethodReturnTypeMacro extends Macro {
  @Override
  public String getName() {
    return "methodReturnType";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    while (place != null) {
      if (place instanceof PsiMethod method) {
        PsiType returnType = method.getReturnType();
        if (returnType != null) {
          return new PsiTypeResult(returnType, place.getProject());
        }
        else {
          break;
        }
      }
      place = place.getParent();
    }

    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }
}