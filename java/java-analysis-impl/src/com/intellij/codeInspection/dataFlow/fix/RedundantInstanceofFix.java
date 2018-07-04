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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class RedundantInstanceofFix implements LocalQuickFix {
  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionsBundle.message("inspection.data.flow.redundant.instanceof.quickfix");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    CommentTracker ct = new CommentTracker();
    if (psiElement instanceof PsiMethodReferenceExpression) {
      String replacement = CommonClassNames.JAVA_UTIL_OBJECTS + "::nonNull";
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
      return;
    }
    String nonNullExpression = null;
    if (psiElement instanceof PsiInstanceOfExpression) {
      nonNullExpression = ct.text(((PsiInstanceOfExpression)psiElement).getOperand());
    }
    else if (psiElement instanceof PsiMethodCallExpression) {
      PsiExpression arg = ArrayUtil.getFirstElement(((PsiMethodCallExpression)psiElement).getArgumentList().getExpressions());
      if (arg == null) return;
      nonNullExpression = ct.text(arg);
    }
    if (nonNullExpression == null) return;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(psiElement.getParent());
    String replacement;
    if (parent instanceof PsiExpression && BoolUtils.isNegation((PsiExpression)parent)) {
      replacement = nonNullExpression + "==null";
      psiElement = parent;
    } else {
      replacement = nonNullExpression + "!=null";
    }
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(ct.replaceAndRestoreComments(psiElement, replacement));
  }
}
