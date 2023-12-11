/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public final class WaitNotInLoopInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("wait.not.in.loop.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WaitNotInLoopVisitor();
  }

  static boolean isCheckedInLoop(PsiMethodCallExpression expression) {
    final PsiElement parent = PsiTreeUtil.getParentOfType(expression, PsiLoopStatement.class, PsiMember.class, PsiLambdaExpression.class);
    if (parent instanceof PsiAnonymousClass || parent instanceof PsiLambdaExpression) {
      // condition is probably checked in a loop somewhere else
      return true;
    }
    if (!(parent instanceof PsiLoopStatement)) {
      return false;
    }
    final PsiStatement body = ((PsiLoopStatement)parent).getBody();
    return PsiTreeUtil.isAncestor(body, expression, true);
  }

  private static class WaitNotInLoopVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!ThreadingUtils.isWaitCall(expression) || isCheckedInLoop(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}