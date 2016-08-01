/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.sillyAssignment;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
public class SillyAssignmentInspection extends SillyAssignmentInspectionBase {

  @Override
  protected LocalQuickFix createRemoveAssignmentFix(PsiReferenceExpression expression) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)parent;
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return null;
      }
    }
    return new RemoveSillyAssignmentFix();
  }

  private static class RemoveSillyAssignmentFix implements LocalQuickFix {

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("assignment.to.itself.quickfix.name");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(element.getParent());
      if (parent instanceof PsiVariable) {
        element.delete();
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression lhs = assignmentExpression.getLExpression();
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (PsiTreeUtil.isAncestor(lhs, element, false)) {
        if (rhs != null) {
          assignmentExpression.replace(rhs);
        }
        else {
          assignmentExpression.delete();
        }
      }
      else {
        final PsiElement grandParent = assignmentExpression.getParent();
        if (grandParent instanceof PsiExpressionStatement) {
          grandParent.delete();
        }
        else {
          assignmentExpression.replace(element);
        }
      }
    }
  }
}
