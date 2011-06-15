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
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldAccessNotGuardedInspection extends BaseJavaLocalInspectionTool {

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CONCURRENCY_ANNOTATION_ISSUES;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unguarded field access";
  }

  @NotNull
  public String getShortName() {
    return "FieldAccessNotGuarded";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }


  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiElement referent = expression.resolve();
      if (referent == null || !(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final String guard = JCiPUtil.findGuardForMember(field);
      if (guard == null) {
        return;
      }
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      if (containingMethod != null && JCiPUtil.isGuardedBy(containingMethod, guard)) {
        return;
      }
      if (containingMethod != null && containingMethod.isConstructor()) {
        return;
      }
      if ("this".equals(guard)) {
        if (containingMethod != null && containingMethod.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
          return;
        }
      }

      if (findLockTryStatement(expression, guard) != null) {
        PsiElement lockExpr = expression;
        while (lockExpr != null) {
          PsiElement child = lockExpr;
          while (child != null) {
            if (isLockGuardStatement(guard, child, "lock")) return;
            child = child.getPrevSibling();
          }
          lockExpr = lockExpr.getParent();
        }
      }

      PsiElement check = expression;
      while (true) {
        final PsiSynchronizedStatement syncStatement = PsiTreeUtil.getParentOfType(check, PsiSynchronizedStatement.class);
        if (syncStatement == null) {
          break;
        }
        final PsiExpression lockExpression = syncStatement.getLockExpression();
        if (lockExpression != null && lockExpression.getText().equals(guard))    //TODO: this isn't quite right,
        {
          return;
        }
        check = syncStatement;
      }
      myHolder.registerProblem(expression, "Access to field <code>#ref</code> outside of declared guards #loc");
    }

    @Nullable
    private static PsiTryStatement findLockTryStatement(PsiReferenceExpression expression, String guard) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(expression, PsiTryStatement.class);
      while (tryStatement != null) {
        PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
        if (finallyBlock != null) {
          for (PsiStatement psiStatement : finallyBlock.getStatements()) {
            if (isLockGuardStatement(guard, psiStatement, "unlock")) {
              return tryStatement;
            }
          }
        }
        tryStatement = PsiTreeUtil.getParentOfType(tryStatement, PsiTryStatement.class);
      }
      return tryStatement;
    }

    private static boolean isLockGuardStatement(String guard, PsiElement element, final String lockMethodStart) {
      if (element instanceof PsiExpressionStatement) {
        final PsiExpression psiExpression = ((PsiExpressionStatement)element).getExpression();
        if (psiExpression instanceof PsiMethodCallExpression) {
          final PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)psiExpression).getMethodExpression();
          final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
          if (qualifierExpression != null && qualifierExpression.getText().startsWith(guard + ".")) {
            final PsiElement resolve = methodExpression.resolve();
            if (resolve instanceof PsiMethod && ((PsiMethod)resolve).getName().startsWith(lockMethodStart)) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }
}