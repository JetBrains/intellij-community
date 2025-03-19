// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

public final class TypeOfVariableMacro extends Macro {
  @Override
  public String getName() {
    return "typeOfVariable";
  }

  @Override
  public String getPresentableName() {
    return "typeOfVariable(VAR)";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length == 0) return null;

    final Project project = context.getProject();
    Result result = params[0].calculateQuickResult(context);
    if (result instanceof PsiElementResult) {
      final PsiElement element = ((PsiElementResult)result).getElement();
      if (element instanceof PsiVariable) {
        return new PsiTypeResult(((PsiVariable)element).getType(), project);
      }
    } else if (result instanceof TextResult) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
      PsiElement place = file.findElementAt(context.getStartOffset());
      final PsiVariable[] vars = MacroUtil.getVariablesVisibleAt(place, "");
      final String name = result.toString();
      for (final PsiVariable var : vars) {
        if (name.equals(var.getName())) return new PsiTypeResult(var.getType(), project);
      }
    }
    return null;
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}