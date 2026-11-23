// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class ArrayVariableMacro extends VariableTypeMacroBase {
  @Override
  public String getName() {
    return "arrayVariable";
  }

  @Override
  protected PsiElement[] getVariables(Expression[] params, final ExpressionContext context) {
    if (params.length != 0) return null;

    final int offset = context.getStartOffset();
    final ArrayList<PsiVariable> array = new ArrayList<>();
    PsiFile file = context.getPsiFile();
    if (file == null) return null;
    PsiElement place = file.findElementAt(offset);
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(place, "");
    for (PsiVariable variable : variables) {
      PsiType type = VariableTypeCalculator.getVarTypeAt(variable, place);
      if (type instanceof PsiArrayType) {
        array.add(variable);
      }
    }
    return array.toArray(new PsiVariable[0]);
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return null;
  }
}
