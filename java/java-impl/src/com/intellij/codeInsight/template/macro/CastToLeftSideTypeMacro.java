// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public final class CastToLeftSideTypeMacro extends Macro {
  @Override
  public String getName() {
    return "castToLeftSideType";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "(A)";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    PsiType leftType = null;
    PsiExpression rightSide = null;
    if (element instanceof PsiAssignmentExpression assignment) {
      leftType  = assignment.getLExpression().getType();
      rightSide = assignment.getRExpression();
    } else if (element instanceof PsiVariable var) {
      leftType = var.getType();
      rightSide = var.getInitializer();
    }

    while (rightSide instanceof PsiTypeCastExpression) rightSide = ((PsiTypeCastExpression) rightSide).getOperand();

    if (leftType != null && rightSide != null && rightSide.getType() != null && !leftType.isAssignableFrom(rightSide.getType())) {
        return new TextResult("("+ leftType.getCanonicalText() + ")");
    }

    return new TextResult("");
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}