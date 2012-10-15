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

/*
 * @author ven
 */
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class CastToLeftSideTypeMacro extends Macro {
  @Override
  public String getName() {
    return "castToLeftSideType";
  }

  @Override
  public String getPresentableName() {
    return CodeInsightBundle.message("macro.cast.to.left.side.type");
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "(A)";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    int offset = context.getStartOffset();
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    PsiElement element = file.findElementAt(offset);
    element = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class, PsiVariable.class);
    PsiType leftType = null;
    PsiExpression rightSide = null;
    if (element instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression) element;
      leftType  = assignment.getLExpression().getType();
      rightSide = assignment.getRExpression();
    } else if (element instanceof PsiVariable) {
      PsiVariable var = (PsiVariable) element;
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