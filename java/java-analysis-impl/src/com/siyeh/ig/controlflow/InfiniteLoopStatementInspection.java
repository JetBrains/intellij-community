/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class InfiniteLoopStatementInspection extends BaseInspection {
  public boolean myIgnoreInThreadTopLevel = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("myIgnoreInThreadTopLevel", JavaAnalysisBundle.message("inspection.infinite.loop.option")));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("infinite.loop.statement.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InfiniteLoopStatementsVisitor();
  }

  private class InfiniteLoopStatementsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkStatement(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkStatement(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkStatement(statement);
    }

    private void checkStatement(PsiStatement statement) {
      if (ControlFlowUtils.statementMayCompleteNormally(statement)) {
        return;
      }
      if (ControlFlowUtils.containsReturn(statement)) {
        return;
      }
      if (ControlFlowUtils.containsSystemExit(statement)) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(statement, PsiSwitchExpression.class) != null && ControlFlowUtils.containsYield(statement)) {
        return;
      }
      if (myIgnoreInThreadTopLevel) {
        PsiElement parent = PsiTreeUtil.findFirstParent(statement, element -> element instanceof PsiMethod ||
                                                                              element instanceof PsiLambdaExpression ||
                                                                              element instanceof PsiAnonymousClass);
        if (parent instanceof PsiMethod method) {
          if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            PsiClass aClass = method.getContainingClass();
            boolean allUsagesAreInThreadStart = StreamEx.ofTree((PsiElement)aClass, element -> StreamEx.of(element.getChildren()))
              .filter(element -> {
                if (element instanceof PsiMethodCallExpression call) {
                  return call.getMethodExpression().isReferenceTo(method);
                }
                if (element instanceof PsiMethodReferenceExpression methodReference) {
                  return methodReference.isReferenceTo(method);
                }
                return false;
              })
              .select(PsiExpression.class)
              .allMatch(inArgument -> {
                if (inArgument instanceof PsiMethodCallExpression) {
                  return isArgumentInThreadConstructor(inArgument);
                }
                if (inArgument instanceof PsiMethodReferenceExpression) {
                  return isInThreadConstructor(inArgument);
                }
                return false;
              });
            if (allUsagesAreInThreadStart) return;
          }
          else if (method.getContainingClass() instanceof PsiAnonymousClass anonymous &&
                   "run".equals(method.getName()) &&
                   method.getParameterList().isEmpty() &&
                   isInThreadConstructor((PsiExpression)anonymous.getParent())) {
            return;
          }
        }
        else if (parent instanceof PsiLambdaExpression lambda && isInThreadConstructor(lambda)) {
          return;
        }
      }
      registerStatementError(statement);
    }

    private static boolean isArgumentInThreadConstructor(@Nullable PsiExpression inArgument) {
      PsiElement skipped = PsiUtil.skipParenthesizedExprUp(inArgument);
      return skipped != null && skipped.getParent() instanceof PsiLambdaExpression lambda && isInThreadConstructor(lambda);
    }

    private static boolean isInThreadConstructor(@NotNull PsiExpression argument) {
      PsiElement argumentParent = PsiUtil.skipParenthesizedExprUp(argument.getParent());
      if (!(argumentParent.getParent() instanceof PsiConstructorCall constructorCall)) return false;
      PsiMethod constructor = constructorCall.resolveConstructor();
      if (constructor == null) return false;
      PsiClass aClass = constructor.getContainingClass();
      return aClass != null && "java.lang.Thread".equals(aClass.getQualifiedName());
    }
  }
}