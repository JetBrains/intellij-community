/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class QualifySuperArgumentFix extends QualifyThisOrSuperArgumentFix {
  public QualifySuperArgumentFix(@NotNull PsiExpression expression, @NotNull PsiClass psiClass) {
    super(expression, psiClass);
  }

  @Override
  protected String getQualifierText() {
    return "super";
  }

  @Override
  protected PsiExpression getQualifier(PsiManager manager) {
    return RefactoringChangeUtil.createSuperExpression(manager, myPsiClass);
  }

  public static void registerQuickFixAction(@NotNull PsiSuperExpression expr, HighlightInfo highlightInfo) {
    LOG.assertTrue(expr.getQualifier() == null);
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
    if (containingClass != null && containingClass.isInterface()) {
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PsiMethodCallExpression.class);
      if (callExpression != null) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
        for (PsiClass superClass : containingClass.getSupers()) {
          if (superClass.isInterface()) {
            final PsiMethodCallExpression copy = (PsiMethodCallExpression)callExpression.copy();
            final PsiExpression superQualifierCopy = copy.getMethodExpression().getQualifierExpression();
            LOG.assertTrue(superQualifierCopy != null);
            superQualifierCopy.delete();
            if (((PsiMethodCallExpression)elementFactory.createExpressionFromText(copy.getText(), superClass)).resolveMethod() != null) {
              QuickFixAction.registerQuickFixAction(highlightInfo, new QualifySuperArgumentFix(expr, superClass));
            }
          }
        }
      }
    }
  }
}
