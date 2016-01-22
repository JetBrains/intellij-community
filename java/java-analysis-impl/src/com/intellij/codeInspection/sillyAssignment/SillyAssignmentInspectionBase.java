/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInspection.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SillyAssignmentInspectionBase extends BaseJavaBatchLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.variable.assigned.to.itself.display.name");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "SillyAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
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
        final PsiExpression initializer = PsiUtil.deparenthesizeExpression(variable.getInitializer());
        if (initializer instanceof PsiAssignmentExpression) {
          final PsiExpression lExpr = PsiUtil.deparenthesizeExpression(((PsiAssignmentExpression)initializer).getLExpression());
          checkExpression(variable, lExpr);
        }
        else {
          checkExpression(variable, initializer);
        }
      }

      private void checkExpression(PsiVariable variable, PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression refExpr = (PsiReferenceExpression)expression;
        final PsiExpression qualifier = refExpr.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression ||
            variable.hasModifierProperty(PsiModifier.STATIC)) {
          if (refExpr.isReferenceTo(variable)) {
            holder.registerProblem(expression, InspectionsBundle.message("assignment.to.declared.variable.problem.descriptor",
                                                                         variable.getName()), ProblemHighlightType.LIKE_UNUSED_SYMBOL);
          }
        }
      }
    };
  }

  private void checkSillyAssignment(PsiAssignmentExpression assignment, ProblemsHolder holder) {
    if (assignment.getOperationTokenType() != JavaTokenType.EQ) return;
    PsiExpression lExpression = assignment.getLExpression();
    PsiExpression rExpression = assignment.getRExpression();
    if (rExpression == null) return;

    lExpression = PsiUtil.deparenthesizeExpression(lExpression);
    if (!(lExpression instanceof PsiReferenceExpression)) return;
    PsiReferenceExpression lRef = (PsiReferenceExpression)lExpression;
    final PsiElement resolved = lRef.resolve();
    if (!(resolved instanceof PsiVariable)) return;
    final PsiVariable variable = (PsiVariable)resolved;

    rExpression = deparenthesizeRExpr(rExpression, variable);

    PsiReferenceExpression rRef;
    if (!(rExpression instanceof PsiReferenceExpression)) {
      if (!(rExpression instanceof PsiAssignmentExpression)) return;
      final PsiAssignmentExpression rAssignmentExpression = (PsiAssignmentExpression)rExpression;
      final PsiExpression assignee = deparenthesizeRExpr(rAssignmentExpression.getLExpression(), variable);
      if (!(assignee instanceof PsiReferenceExpression)) return;
      rRef = (PsiReferenceExpression)assignee;
    } else {
      rRef = (PsiReferenceExpression)rExpression;
    }
    PsiManager manager = assignment.getManager();
    if (!sameInstanceReferences(lRef, rRef, manager)) return;
    holder.registerProblem(assignment, InspectionsBundle.message("assignment.to.itself.problem.descriptor", variable.getName()),
                           ProblemHighlightType.LIKE_UNUSED_SYMBOL, createRemoveAssignmentFix());
  }

  private static PsiExpression deparenthesizeRExpr(PsiExpression rExpression, PsiVariable variable) {
    rExpression = PsiUtil.skipParenthesizedExprDown(rExpression);
    if (rExpression instanceof PsiTypeCastExpression) {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)rExpression;
      final PsiExpression operand = typeCastExpression.getOperand();
      final PsiTypeElement castTypeElement = typeCastExpression.getCastType();
      if (castTypeElement == null || operand == null) return null;
      final PsiType castType = castTypeElement.getType();
      if (castType instanceof PsiPrimitiveType) {
        if (variable.getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
          return rExpression;
        }
        else if (TypeUtils.isNarrowingConversion(operand.getType(), castType)) {
          return null;
        }
      }
      return deparenthesizeRExpr(operand, variable);
    }
    return rExpression;
  }

  protected LocalQuickFix createRemoveAssignmentFix() {
    return null;
  }

  /**
   * @return true if both expressions resolve to the same variable/class or field in the same instance of the class
   */
  private static boolean sameInstanceReferences(@Nullable PsiJavaCodeReferenceElement lRef, @Nullable PsiJavaCodeReferenceElement rRef, PsiManager manager) {
    if (lRef == null && rRef == null) return true;
    if (lRef == null || rRef == null) return false;
    PsiElement lResolved = lRef.resolve();
    PsiElement rResolved = rRef.resolve();
    if (!manager.areElementsEquivalent(lResolved, rResolved)) return false;
    if (!(lResolved instanceof PsiVariable)) return false;
    final PsiVariable variable = (PsiVariable)lResolved;
    if (variable.hasModifierProperty(PsiModifier.STATIC)) return true;

    final PsiElement lQualifier = lRef.getQualifier();
    final PsiElement rQualifier = rRef.getQualifier();
    if (lQualifier instanceof PsiJavaCodeReferenceElement && rQualifier instanceof PsiJavaCodeReferenceElement) {
      return sameInstanceReferences((PsiJavaCodeReferenceElement)lQualifier, (PsiJavaCodeReferenceElement)rQualifier, manager);
    }

    if (Comparing.equal(lQualifier, rQualifier)) return true;
    boolean lThis = lQualifier == null || lQualifier instanceof PsiThisExpression || lQualifier instanceof PsiSuperExpression;
    boolean rThis = rQualifier == null || rQualifier instanceof PsiThisExpression || rQualifier instanceof PsiSuperExpression;
    if (lThis && rThis) {
      final PsiJavaCodeReferenceElement llQualifier = getQualifier(lQualifier);
      final PsiJavaCodeReferenceElement rrQualifier = getQualifier(rQualifier);
      return sameInstanceReferences(llQualifier, rrQualifier, manager);
    }
    return false;
  }

  private static PsiJavaCodeReferenceElement getQualifier(PsiElement qualifier) {
    if (qualifier instanceof PsiThisExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)qualifier).getQualifier();
      if (thisQualifier != null) {
        final PsiClass innerMostClass = PsiTreeUtil.getParentOfType(thisQualifier, PsiClass.class);
        if (innerMostClass == thisQualifier.resolve()) {
          return null;
        }
      }
      return thisQualifier;
    }
    if (qualifier != null) {
      return  ((PsiSuperExpression)qualifier).getQualifier();
    }
    return null;
  }
}
