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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceVariableFix;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MethodCallInLoopConditionInspection extends BaseInspection {

  public boolean ignoreIterationMethods = true;

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new IntroduceVariableFix(false) {

      @Override
      public @NotNull String getFamilyName() {
        return InspectionGadgetsBundle.message("introduce.variable.may.change.semantics.quickfix");
      }
    };
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("method.call.in.loop.condition.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreIterationMethods", InspectionGadgetsBundle.message("inspection.method.call.in.loop.ignore.known.methods.option")));
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new MethodCallInLoopConditionVisitor();
  }

  private class MethodCallInLoopConditionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoop(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoop(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkLoop(statement);
    }

    public void checkLoop(@NotNull PsiConditionalLoopStatement statement) {
      final PsiExpression condition = statement.getCondition();
      if (condition == null) return;
      checkForMethodCalls(condition);
    }

    private void checkForMethodCalls(PsiExpression condition) {
      condition.accept(new JavaRecursiveElementWalkingVisitor() {

          @Override
          public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            if (ignoreIterationMethods) {
              if (isIterationMethod(expression) || isCallToCasMethod(expression)) {
                return;
              }
            }
            registerMethodCallError(expression);
          }

        private static boolean isIterationMethod(@NotNull PsiMethodCallExpression expression) {
          return MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_UTIL_ITERATOR, PsiTypes.booleanType(), "hasNext") ||
                 MethodCallUtils.isCallToMethod(expression, "java.util.ListIterator", PsiTypes.booleanType(), "hasPrevious") ||
                 MethodCallUtils.isCallToMethod(expression, "java.sql.ResultSet", PsiTypes.booleanType(), "next") ||
                 MethodCallUtils.isCallToMethod(expression, "java.util.Enumeration", PsiTypes.booleanType(), "hasMoreElements") ||
                 MethodCallUtils.isCallToMethod(expression, CommonClassNames.JAVA_UTIL_QUEUE, null, "poll") ||
                 MethodCallUtils.isCallToMethod(expression, "java.lang.ref.ReferenceQueue", null, "poll");
        }

        private static boolean isCallToCasMethod(@NotNull PsiMethodCallExpression expression) {
          final String methodName = MethodCallUtils.getMethodName(expression);
          if (!"weakCompareAndSet".equals(methodName) && !"compareAndSet".equals(methodName)) {
            return false;
          }
          final PsiMethod method = expression.resolveMethod();
          if (method == null) {
            return false;
          }
          final PsiClass containingClass = method.getContainingClass();
          if (containingClass == null) {
            return false;
          }
          final String qualifiedName = containingClass.getQualifiedName();
          return qualifiedName != null && qualifiedName.startsWith("java.util.concurrent.atomic.");
        }
      });
    }
  }
}
