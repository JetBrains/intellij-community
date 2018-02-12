/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SimplifyToAssignmentFix implements LocalQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.data.flow.simplify.to.assignment.quickfix.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;

    final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(psiElement, PsiAssignmentExpression.class);
    if (assignmentExpression == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final String lExpressionText = assignmentExpression.getLExpression().getText();
    final PsiExpression rExpression = assignmentExpression.getRExpression();
    final String rExpressionText = rExpression != null ? rExpression.getText() : "";
    assignmentExpression.replace(factory.createExpressionFromText(lExpressionText + " = " + rExpressionText, psiElement));
  }
}
