// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.sillyAssignment;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class SillyAssignmentInspection extends AbstractBaseJavaLocalInspectionTool {

  private static LocalQuickFix createRemoveAssignmentFix(PsiExpression expression) {
    final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiVariable variable && variable.hasModifierProperty(PsiModifier.FINAL)) {
      return null;
    }
    return new RemoveSillyAssignmentFix();
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return "SillyAssignment";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
        checkSillyAssignment(expression, holder);
      }

      @Override public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        visitElement(expression);
      }

      @Override public void visitVariable(final @NotNull PsiVariable variable) {
        final PsiExpression initializer = PsiUtil.deparenthesizeExpression(variable.getInitializer());
        if (initializer instanceof PsiAssignmentExpression assignment) {
          if (assignment.getOperationTokenType() != JavaTokenType.EQ) {
            return; // skip red unfixable code for local variables and green unfixable code for fields
          } 
          checkExpression(variable, PsiUtil.deparenthesizeExpression(assignment.getLExpression()));
        }
        else {
          checkExpression(variable, initializer);
        }
      }

      private void checkExpression(PsiVariable variable, PsiExpression expression) {
        if (!(expression instanceof PsiReferenceExpression refExpr)) {
          return;
        }
        final PsiExpression qualifier = refExpr.getQualifierExpression();
        if ((qualifier == null || qualifier instanceof PsiQualifiedExpression || variable.hasModifierProperty(PsiModifier.STATIC)) 
            && refExpr.isReferenceTo(variable)) {
          holder.registerProblem(refExpr,
                                 JavaBundle.message("assignment.to.declared.variable.problem.descriptor", variable.getName()),
                                 LocalQuickFix.notNullElements(createRemoveAssignmentFix(refExpr)));
        }
      }
    };
  }

  private static void checkSillyAssignment(@NotNull PsiAssignmentExpression assignment, @NotNull ProblemsHolder holder) {
    if (assignment.getOperationTokenType() != JavaTokenType.EQ) return;
    PsiExpression leftExpression = assignment.getLExpression();
    PsiExpression rightExpression = assignment.getRExpression();
    if (rightExpression == null) return;

    leftExpression = PsiUtil.deparenthesizeExpression(leftExpression);
    PsiExpression leftArrayExpressionOrItself = getArrayExpressionOrItself(leftExpression);
    if (!(leftArrayExpressionOrItself instanceof PsiReferenceExpression lRef)) return;
    final PsiElement resolved = lRef.resolve();
    if (!(resolved instanceof PsiVariable variable)) return;

    rightExpression = deparenthesizeRExpr(rightExpression, variable);
    PsiExpression rightArrayExpressionOrItself = getArrayExpressionOrItself(rightExpression);

    if (!(rightArrayExpressionOrItself instanceof PsiReferenceExpression)) {
      if (!(rightArrayExpressionOrItself instanceof PsiAssignmentExpression rAssignmentExpression)) return;
      rightExpression = deparenthesizeRExpr(rAssignmentExpression.getLExpression(), variable);
    }
    if (rightExpression == null) return;

    EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
    if (!checker.expressionsAreEquivalent(leftExpression, rightExpression) || SideEffectChecker.mayHaveSideEffects(leftExpression)) return;
    String message = leftExpression instanceof PsiArrayAccessExpression
                     ? JavaBundle.message("assignment.array.element.to.itself.problem.descriptor")
                     : JavaBundle.message("assignment.to.itself.problem.descriptor", variable.getName());
    holder.registerProblem(rightExpression, message, createRemoveAssignmentFix(rightExpression));
  }

  private static PsiExpression getArrayExpressionOrItself(PsiExpression expression) {
    return expression instanceof PsiArrayAccessExpression ? ((PsiArrayAccessExpression)expression).getArrayExpression() : expression;
  }

  private static PsiExpression deparenthesizeRExpr(PsiExpression rExpression, PsiVariable variable) {
    rExpression = PsiUtil.skipParenthesizedExprDown(rExpression);
    if (rExpression instanceof PsiTypeCastExpression typeCastExpression) {
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

  private static class RemoveSillyAssignmentFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionsBundle.message("assignment.to.itself.quickfix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiExpression expression = ObjectUtils.tryCast(element, PsiExpression.class);
      if (!(getArrayExpressionOrItself(expression) instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      if (parent instanceof PsiVariable) {
        expression.delete();
      }
      else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
        PsiElement grandParent = PsiUtil.skipParenthesizedExprUp(assignmentExpression.getParent());
        if (grandParent instanceof PsiAssignmentExpression) {
          grandParent.replace(assignmentExpression);
        }
        else if (grandParent instanceof PsiExpressionStatement) {
          grandParent.delete();
        }
        else if (grandParent instanceof PsiVariable variable) {
          PsiExpression rhs = assignmentExpression.getRExpression();
          variable.setInitializer(rhs == null ? null : (PsiExpression)rhs.copy());
        }
        else {
          assignmentExpression.replace(expression);
        }
      }
    }
  }
}
