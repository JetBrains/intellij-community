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
package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String delegateClass;
  private final boolean myEnumConstant;
  private static final Logger LOGGER = Logger.getInstance("#" + ReplaceStaticVariableAccess.class.getName());

  public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass, boolean enumConstant) {
    super(expression);
    this.expression = expression;
    this.delegateClass = delegateClass;
    myEnumConstant = enumConstant;
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myEnumConstant) {
      final PsiSwitchLabelStatement switchStatement = PsiTreeUtil.getParentOfType(expression, PsiSwitchLabelStatement.class);
      if (switchStatement != null) {
        MutationUtils.replaceExpression(expression.getReferenceName(), expression);
        return;
      }
    }
    boolean replaceWithGetEnumValue = myEnumConstant;
    if (replaceWithGetEnumValue) {
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class);
      if (callExpression != null) {
        final PsiElement resolved = callExpression.getMethodExpression().resolve();
        if (resolved instanceof PsiMethod) {
          final PsiParameter[] parameters = ((PsiMethod)resolved).getParameterList().getParameters();
          final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
          final int idx = ArrayUtil.find(args, expression);
          if (idx != -1 && parameters[idx].getType().getCanonicalText().equals(delegateClass)) {
            replaceWithGetEnumValue = false;
          }
        }
      }
      else {
        final PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class);
        if (returnStatement != null) {
          final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
          LOGGER.assertTrue(psiMethod != null);
          final PsiType returnType = psiMethod.getReturnType();
          if (returnType != null && returnType.getCanonicalText().equals(delegateClass)) {
            replaceWithGetEnumValue = false;
          }
        }
      }
    }
    final String link = replaceWithGetEnumValue ? "." + PropertyUtil.suggestGetterName("value", expression.getType()) + "()" : "";
    MutationUtils.replaceExpression(delegateClass + '.' + expression.getReferenceName() + link, expression);
  }
}
