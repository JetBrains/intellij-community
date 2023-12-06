/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NonAtomicOperationOnVolatileFieldInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.atomic.operation.on.volatile.field.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonAtomicOperationOnVolatileFieldVisitor();
  }

  private static class NonAtomicOperationOnVolatileFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getRExpression());
      if (rhs == null) {
        return;
      }
      final PsiExpression lhs = expression.getLExpression();
      final PsiReferenceExpression volatileFieldRef = findNonSynchronizedVolatileFieldRef(lhs);
      if (volatileFieldRef == null) {
        return;
      }
      final PsiElement referenceNameElement = volatileFieldRef.getReferenceNameElement();
      if (referenceNameElement == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSEQ) ||
          tokenType.equals(JavaTokenType.MINUSEQ) ||
          tokenType.equals(JavaTokenType.ASTERISKEQ) ||
          tokenType.equals(JavaTokenType.DIVEQ) ||
          tokenType.equals(JavaTokenType.ANDEQ) ||
          tokenType.equals(JavaTokenType.OREQ) ||
          tokenType.equals(JavaTokenType.XOREQ) ||
          tokenType.equals(JavaTokenType.PERCEQ) ||
          tokenType.equals(JavaTokenType.LTLTEQ) ||
          tokenType.equals(JavaTokenType.GTGTEQ) ||
          tokenType.equals(JavaTokenType.GTGTGTEQ)) {
        registerError(referenceNameElement);
        return;
      }
      rhs.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
          if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lhs, reference)) {
            stopWalking();
            registerError(referenceNameElement);
            return;
          }
          super.visitReferenceExpression(reference);
        }
      });
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      if (!PsiUtil.isIncrementDecrementOperation(expression)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiReferenceExpression volatileFieldRef = findNonSynchronizedVolatileFieldRef(operand);
      if (volatileFieldRef == null) {
        return;
      }
      final PsiElement referenceNameElement = volatileFieldRef.getReferenceNameElement();
      if (referenceNameElement != null) {
        registerError(referenceNameElement);
      }
    }

    @Nullable
    private static PsiReferenceExpression findNonSynchronizedVolatileFieldRef(PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiReferenceExpression reference)) {
        return null;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField field)) {
        return null;
      }
      if (!field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return null;
      }
      if (SynchronizationUtil.isInSynchronizedContext(reference)) {
        return null;
      }
      return (PsiReferenceExpression)expression;
    }
  }
}