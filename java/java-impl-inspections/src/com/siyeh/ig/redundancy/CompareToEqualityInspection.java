// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompareToEqualityInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher COMPARE_TO =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_COMPARABLE, "compareTo").parameterCount(1);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
        super.visitBinaryExpression(expression);
        final IElementType tokenType = expression.getOperationTokenType();
        if (!tokenType.equals(JavaTokenType.EQEQ) && !tokenType.equals(JavaTokenType.NE)) return;
        final PsiMethodCallExpression call = compareToComparedWithZero(expression);
        if (call == null) return;
        final PsiExpression argument = call.getArgumentList().getExpressions()[0];
        // compareTo(null) throws a NullPointerException, but equals(null) returns false, so the replacement
        // would change behavior when the argument may be null.
        if (NullabilityUtil.getExpressionNullability(argument) == Nullability.NULLABLE) return;
        PsiMethod method = call.resolveMethod();
        if (method == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        final boolean replaceWithEquality = InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ENUM);
        boolean allowedType = replaceWithEquality || switch (aClass.getQualifiedName()) {
          case CommonClassNames.JAVA_LANG_BYTE,
               CommonClassNames.JAVA_LANG_SHORT,
               CommonClassNames.JAVA_LANG_INTEGER,
               CommonClassNames.JAVA_LANG_LONG,
               CommonClassNames.JAVA_LANG_FLOAT,
               CommonClassNames.JAVA_LANG_DOUBLE,
               CommonClassNames.JAVA_LANG_STRING,
               "java.math.BigInteger" -> true;
          // note BigDecimal's compareTo() method is not consistent with its equals() method
          case null, default -> false;
        };
        if (!allowedType) return;
        final PsiElement anchor =
          !isOnTheFly || isVisibleHighlight(expression) ? call.getMethodExpression().getReferenceNameElement() : expression;
        assert anchor != null;
        holder.registerProblem(anchor,
                               InspectionGadgetsBundle.message("string.compare.to.equality.problem.descriptor"),
                               new ReplaceWithEqualsFix(replaceWithEquality));
      }
    };
  }

  private boolean isVisibleHighlight(@NotNull PsiElement element) {
    return !InspectionProjectProfileManager.isInformationLevel(getShortName(), element);
  }

  /// Returns the 'String.compareTo(String)' call if one operand is such a call and the other is a literal 0.
  private static @Nullable PsiMethodCallExpression compareToComparedWithZero(@NotNull PsiBinaryExpression expression) {
    final PsiExpression left = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
    final PsiExpression right = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
    if (left == null || right == null) return null;
    final PsiMethodCallExpression leftCall = asCompareTo(left);
    if (leftCall != null && ExpressionUtils.isZero(right)) return leftCall;
    final PsiMethodCallExpression rightCall = asCompareTo(right);
    if (rightCall != null && ExpressionUtils.isZero(left)) return rightCall;
    return null;
  }

  private static @Nullable PsiMethodCallExpression asCompareTo(@NotNull PsiExpression expression) {
    return expression instanceof PsiMethodCallExpression call && COMPARE_TO.test(call) ? call : null;
  }

  private static class ReplaceWithEqualsFix extends PsiUpdateModCommandQuickFix {

    private final boolean myReplaceWithEquality;

    private ReplaceWithEqualsFix(boolean replaceWithEquality) {
      myReplaceWithEquality = replaceWithEquality;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return myReplaceWithEquality
       ? InspectionGadgetsBundle.message("string.compare.to.equality.quickfix")
       : CommonQuickFixBundle.message("fix.replace.with.x.call", "equals()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(PsiTreeUtil.getParentOfType(element, false, PsiBinaryExpression.class) instanceof PsiBinaryExpression expression)) return;
      final boolean negated = expression.getOperationTokenType().equals(JavaTokenType.NE);
      final PsiMethodCallExpression call = compareToComparedWithZero(expression);
      if (call == null) return;
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      final PsiExpression argument = call.getArgumentList().getExpressions()[0];
      final CommentTracker ct = new CommentTracker();
      final StringBuilder replacement = new StringBuilder();
      if (myReplaceWithEquality) {
        replacement.append(qualifier == null ? "this" : ct.text(qualifier));
        replacement.append(negated ? "!=" : "==").append(ct.text(argument));
      }
      else {
        if (negated) replacement.append('!');
        if (qualifier != null) replacement.append(ct.text(qualifier)).append('.');
        replacement.append("equals(").append(ct.text(argument)).append(')');
      }
      ct.replaceAndRestoreComments(expression, replacement.toString());
    }
  }
}
