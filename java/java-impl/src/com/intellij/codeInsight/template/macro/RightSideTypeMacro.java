/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;


/**
 * @author ven
 */
public class RightSideTypeMacro extends Macro {
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
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) element;
      PsiExpression rhs = assignment.getRExpression();
      if (rhs == null) return null;
      final PsiType rhsType = rhs.getType();
      if (rhsType == null) return null;
      return new PsiTypeResult(rhsType, project);
    } else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable) element;
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
