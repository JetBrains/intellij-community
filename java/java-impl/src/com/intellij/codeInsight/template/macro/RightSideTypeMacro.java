// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.JavaCodeContextType;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;


public final class RightSideTypeMacro extends Macro {
  @Override
  public String getName() {
    return "rightSideType";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    if (element instanceof PsiAssignmentExpression assignment) {
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return null;
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) return null;
      return new PsiTypeResult(rhsType, project);
    } else if (element instanceof PsiVariable var) {
      PsiExpression initializer = var.getInitializer();
      if (initializer == null) return null;
      PsiType type = CommonJavaRefactoringUtil.getTypeByExpression(initializer);
      if (type == null) return null;
      return new PsiTypeResult(type, project);
    }
    return null;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
