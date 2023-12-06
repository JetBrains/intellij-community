// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class QualifySuperArgumentFix extends QualifyThisOrSuperArgumentFix {
  private QualifySuperArgumentFix(@NotNull PsiExpression expression, @NotNull PsiClass psiClass) {
    super(expression, psiClass);
  }

  @Override
  protected String getQualifierText() {
    return PsiKeyword.SUPER;
  }

  @Override
  protected PsiExpression getQualifier(PsiManager manager) {
    return RefactoringChangeUtil.createSuperExpression(manager, myPsiClass);
  }

  public static void registerQuickFixAction(@NotNull PsiSuperExpression expr, @NotNull HighlightInfo.Builder highlightInfo) {
    LOG.assertTrue(expr.getQualifier() == null);
    final PsiClass containingClass = PsiTreeUtil.getParentOfType(expr, PsiClass.class);
    if (containingClass != null) {
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(expr, PsiMethodCallExpression.class);
      if (callExpression != null) {
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(callExpression.getProject());
        for (PsiClass superClass : containingClass.getSupers()) {
          if (superClass.isInterface()) {
            final PsiMethodCallExpression copy = (PsiMethodCallExpression)callExpression.copy();
            final PsiExpression superQualifierCopy = copy.getMethodExpression().getQualifierExpression();
            LOG.assertTrue(superQualifierCopy != null);
            superQualifierCopy.delete();
            PsiMethod method;
            try {
              method = ((PsiMethodCallExpression)elementFactory.createExpressionFromText(copy.getText(), superClass)).resolveMethod();
            }
            catch (IncorrectOperationException e) {
              LOG.info(e);
              return;
            }
            if (method != null && !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
              var action = new QualifySuperArgumentFix(expr, superClass);
              highlightInfo.registerFix(action, null, null, null, null);
            }
          }
        }
      }
    }
  }
}
