/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ArrayContentsAssignedVisitor extends JavaRecursiveElementWalkingVisitor {

  private boolean assigned;
  private final PsiVariable variable;

  ArrayContentsAssignedVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitAssignmentExpression(
    @NotNull PsiAssignmentExpression assignment) {
    if (assigned) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    final PsiExpression lhs = assignment.getLExpression();
    final PsiExpression arrayExpression = getDeepArrayExpression(lhs);
    if (!(arrayExpression instanceof PsiReferenceExpression referenceExpression)) {
      return;
    }
    final PsiElement referent = referenceExpression.resolve();
    if (referent == null) {
      return;
    }
    if (referent.equals(variable)) {
      assigned = true;
    }
  }

  @Override
  public void visitUnaryExpression(
    @NotNull PsiUnaryExpression expression) {
    if (assigned) {
      return;
    }
    super.visitUnaryExpression(expression);
    final IElementType tokenType = expression.getOperationTokenType();
    if (!(tokenType.equals(JavaTokenType.PLUSPLUS) ||
          tokenType.equals(JavaTokenType.MINUSMINUS))) {
      return;
    }
    final PsiExpression operand = expression.getOperand();
    final PsiExpression arrayExpression = getDeepArrayExpression(operand);
    if (!(arrayExpression instanceof PsiReferenceExpression referenceExpression)) {
      return;
    }
    final PsiElement referent = referenceExpression.resolve();
    if (referent == null) {
      return;
    }
    if (referent.equals(variable)) {
      assigned = true;
    }
  }

  private static @Nullable PsiExpression getDeepArrayExpression(
    PsiExpression expression) {
    if (!(expression instanceof PsiArrayAccessExpression)) {
      return null;
    }
    PsiExpression arrayExpression =
      ((PsiArrayAccessExpression)expression).getArrayExpression();
    while (arrayExpression instanceof PsiArrayAccessExpression arrayAccessExpression) {
      arrayExpression = arrayAccessExpression.getArrayExpression();
    }
    return arrayExpression;
  }

  public boolean isAssigned() {
    return assigned;
  }
}
