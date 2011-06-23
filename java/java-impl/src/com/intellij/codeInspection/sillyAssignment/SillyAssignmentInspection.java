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
package com.intellij.codeInspection.sillyAssignment;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 15-Nov-2005
 */
public class SillyAssignmentInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getDisplayName() {
    return JavaErrorMessages.message("assignment.to.itself");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "SillyAssignment";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        checkSillyAssignment(expression, holder);
      }

      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override public void visitVariable(final PsiVariable variable) {
        final PsiExpression initializer = variable.getInitializer();
        if (initializer instanceof PsiAssignmentExpression) {
          final PsiExpression lExpr = ((PsiAssignmentExpression)initializer).getLExpression();
          if (lExpr instanceof PsiReferenceExpression) {
            final PsiReferenceExpression refExpr = (PsiReferenceExpression)lExpr;
            if (!refExpr.isQualified() && refExpr.isReferenceTo(variable)) {
              holder.registerProblem(lExpr, JavaErrorMessages.message("assignment.to.declared.variable", variable.getName()), 
                                     ProblemHighlightType.LIKE_UNUSED_SYMBOL, (LocalQuickFix[])null);
            }
          }
        }
      }
    };
  }

  private static void checkSillyAssignment(PsiAssignmentExpression assignment, ProblemsHolder holder) {
    if (assignment.getOperationTokenType() != JavaTokenType.EQ) return;
    PsiExpression lExpression = assignment.getLExpression();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;
    lExpression = PsiUtil.deparenthesizeExpression(lExpression);
    rExpression = PsiUtil.deparenthesizeExpression(rExpression);
    if (!(lExpression instanceof PsiReferenceExpression)) return;
    PsiReferenceExpression rRef;
    if (!(rExpression instanceof PsiReferenceExpression)) {
      if (!(rExpression instanceof PsiAssignmentExpression)) return;
      final PsiAssignmentExpression rAssignmentExpression = (PsiAssignmentExpression)rExpression;
      final PsiExpression assignee = rAssignmentExpression.getLExpression();
      if (!(assignee instanceof PsiReferenceExpression)) return;
      rRef = (PsiReferenceExpression)assignee;
    } else {
      rRef = (PsiReferenceExpression)rExpression;
    }
    PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
    PsiManager manager = assignment.getManager();
    if (!sameInstanceReferences(lRef, rRef, manager)) return;
    holder.registerProblem(assignment, JavaErrorMessages.message("assignment.to.itself"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, (LocalQuickFix[])null);
  }

  /**
   * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
   */
  private static boolean sameInstanceReferences(PsiReferenceExpression lRef, PsiReferenceExpression rRef, PsiManager manager) {
    PsiElement lResolved = lRef.resolve();
    PsiElement rResolved = rRef.resolve();
    if (!manager.areElementsEquivalent(lResolved, rResolved)) return false;

    PsiExpression lQualifier = lRef.getQualifierExpression();
    PsiExpression rQualifier = rRef.getQualifierExpression();
    if (lQualifier instanceof PsiReferenceExpression && rQualifier instanceof PsiReferenceExpression) {
      return sameInstanceReferences((PsiReferenceExpression)lQualifier, (PsiReferenceExpression)rQualifier, manager);
    }
    if (Comparing.equal(lQualifier, rQualifier)) return true;
    boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression;
    boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression;
    return lThis && rThis;
  }

}
