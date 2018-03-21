// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MethodReturnTypeMacro extends Macro {
  @Override
  public String getName() {
    return "methodReturnType";
  }

  @Override
  public String getPresentableName() {
    return "methodReturnType()";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    while (place != null) {
      if (place instanceof PsiMethod) {
        PsiType returnType = ((PsiMethod)place).getReturnType();
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