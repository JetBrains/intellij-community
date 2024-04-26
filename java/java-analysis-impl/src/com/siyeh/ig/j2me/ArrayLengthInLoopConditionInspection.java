/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ArrayLengthInLoopConditionInspection extends BaseInspection {

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "array.length.in.loop.condition.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayLengthInLoopConditionVisitor();
  }

  private static class ArrayLengthInLoopConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkCondition(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkCondition(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkCondition(statement);
    }

    public void checkCondition(@NotNull PsiConditionalLoopStatement statement) {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      checkForArrayLength(condition);
    }

    private void checkForArrayLength(PsiExpression condition) {
      condition.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
          super.visitReferenceExpression(expression);
          if (ExpressionUtils.getArrayFromLengthExpression(expression) != null) {
            registerError(Objects.requireNonNull(expression.getReferenceNameElement()));
          }
        }
      });
    }
  }
}