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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class SafeDeleteFieldWriteReference extends SafeDeleteReferenceUsageInfo {
  private final PsiAssignmentExpression myExpression;

  public SafeDeleteFieldWriteReference(PsiAssignmentExpression expr, PsiField referencedElement) {
    super(expr, referencedElement, safeRemoveRHS(expr));
    myExpression = expr;
  }

  private static boolean safeRemoveRHS(PsiAssignmentExpression expression) {
    final PsiExpression rExpression = expression.getRExpression();
    final PsiElement parent = expression.getParent();
    return RefactoringUtil.verifySafeCopyExpression(rExpression) == RefactoringUtil.EXPR_COPY_SAFE
                            && parent instanceof PsiExpressionStatement
                            && ((PsiExpressionStatement) parent).getExpression() == expression;
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    myExpression.getParent().delete();
  }

}
