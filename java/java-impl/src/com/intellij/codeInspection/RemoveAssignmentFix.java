/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class RemoveAssignmentFix extends RemoveInitializerFix {
  @NotNull
  @Override
  public String getName() {
    return InspectionsBundle.message("inspection.unused.assignment.remove.assignment.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent;
    if (element instanceof PsiReferenceExpression) {
      parent = element.getParent();
    } else {
      parent = element;
    }
    if (!(parent instanceof PsiAssignmentExpression)) return;
    final PsiExpression rExpression = ((PsiAssignmentExpression)parent).getRExpression();
    final PsiElement gParent = parent.getParent();
    if ((gParent instanceof PsiExpression || gParent instanceof PsiExpressionList) && rExpression != null) {
      if (!FileModificationService.getInstance().prepareFileForWrite(gParent.getContainingFile())) return;
      if (gParent instanceof PsiParenthesizedExpression) {
        gParent.replace(rExpression);
      } else {
        parent.replace(rExpression);
      }
      return;
    }

    PsiElement resolve = null;
    if (element instanceof PsiReferenceExpression) {
      resolve = ((PsiReferenceExpression)element).resolve();
    } else {
      final PsiExpression lExpr = PsiUtil.deparenthesizeExpression(((PsiAssignmentExpression)parent).getLExpression());
      if (lExpr instanceof PsiReferenceExpression) {
        resolve = ((PsiReferenceExpression)lExpr).resolve();
      }
    }
    if (!(resolve instanceof PsiVariable)) return;
    sideEffectAwareRemove(project, rExpression, parent, (PsiVariable)resolve);
  }
}
