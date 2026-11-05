// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
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
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.search.PsiElementProcessor;
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

import java.util.concurrent.atomic.AtomicInteger;

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
        final boolean nullable = NullabilityUtil.getExpressionNullability(argument) == Nullability.NULLABLE;
        final PsiMethod method = call.resolveMethod();
        if (method == null) return;
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        final boolean replaceWithEquality = InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ENUM);
        final boolean mayChangeSemantics = nullable || (!replaceWithEquality && !isAllowedClass(aClass)) || isPartOfSequence(call);
        if (!isOnTheFly && mayChangeSemantics) return;
        final PsiElement anchor =
          !isOnTheFly || isVisibleHighlight(expression) ? call.getMethodExpression().getReferenceNameElement() : expression;
        assert anchor != null;
        final ProblemHighlightType highlightType =
          mayChangeSemantics ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        holder.registerProblem(anchor, InspectionGadgetsBundle.message("compare.to.equality.problem.descriptor"),
                               highlightType, new ReplaceWithEqualsFix(replaceWithEquality, mayChangeSemantics));
      }

      /// do not highlight when there are several compareTo() calls nearby that can't be replaced.
      private static boolean isPartOfSequence(@NotNull PsiMethodCallExpression call) {
        final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return false;
        PsiElement block = PsiTreeUtil.getParentOfType(call, PsiCodeBlock.class, true, PsiLambdaExpression.class, PsiMember.class);
        if (block == null) return false;
        if (block.getParent().getParent() instanceof PsiIfStatement) {
          block = PsiTreeUtil.getParentOfType(block, PsiCodeBlock.class, true, PsiLambdaExpression.class, PsiMember.class);
        }
        AtomicInteger count = new AtomicInteger(0);
        PsiTreeUtil.processElements(block, PsiMethodCallExpression.class, new PsiElementProcessor.CollectElementsWithLimit<>(2) {
          @Override
          public boolean execute(@NotNull PsiMethodCallExpression c) {
            if (!COMPARE_TO.test(c) || call == c) return true;
            if (PsiUtil.skipParenthesizedExprUp(c.getParent()) instanceof PsiBinaryExpression binary) {
              final IElementType tokenType = binary.getOperationTokenType();
              if ((tokenType.equals(JavaTokenType.EQEQ) || tokenType.equals(JavaTokenType.NE)) 
                  && (ExpressionUtils.isZero(binary.getROperand()) || ExpressionUtils.isZero(binary.getLOperand()))) return true;
            }
            return count.incrementAndGet() < 2;
          }
        });
        return count.get() > 1;
      }

      private static boolean isAllowedClass(PsiClass aClass) {
        return switch (aClass.getQualifiedName()) {
          case CommonClassNames.JAVA_LANG_CHARACTER,
               CommonClassNames.JAVA_LANG_BOOLEAN,
               CommonClassNames.JAVA_LANG_BYTE,
               CommonClassNames.JAVA_LANG_SHORT,
               CommonClassNames.JAVA_LANG_INTEGER,
               CommonClassNames.JAVA_LANG_LONG,
               CommonClassNames.JAVA_LANG_FLOAT,
               CommonClassNames.JAVA_LANG_DOUBLE,
               CommonClassNames.JAVA_LANG_STRING,
               "java.math.BigInteger",
               CommonClassNames.JAVA_TIME_LOCAL_TIME,
               "java.time.OffsetTime",
               "java.time.MonthDay",
               "java.time.Year",
               "java.time.YearMonth",
               "java.time.Instant",
               "java.time.Duration",
               "java.time.ZoneOffset",
               "java.time.OffsetDateTime" -> true;
          // note BigDecimal's compareTo() method is not consistent with its equals() method
          case null, default -> false;
        };
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
    if (left instanceof PsiMethodCallExpression call && COMPARE_TO.test(call) && ExpressionUtils.isZero(right)) return call;
    if (right instanceof PsiMethodCallExpression call && COMPARE_TO.test(call) && ExpressionUtils.isZero(left)) return call;
    return null;
  }

  private static class ReplaceWithEqualsFix extends PsiUpdateModCommandQuickFix {

    private final boolean myReplaceWithEquality;
    private final boolean myMayChangeSemantics;

    private ReplaceWithEqualsFix(boolean replaceWithEquality, boolean mayChangeSemantics) {
      myReplaceWithEquality = replaceWithEquality;
      myMayChangeSemantics = mayChangeSemantics;
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      if (myReplaceWithEquality) {
        return myMayChangeSemantics
               ? InspectionGadgetsBundle.message("compare.to.equality.quickfix.may.change.semantics")
               : InspectionGadgetsBundle.message("compare.to.equality.quickfix");
      }
      return myMayChangeSemantics
             ? InspectionGadgetsBundle.message("compare.to.equality.convert.may.change.semantics.quickfix")
             : InspectionGadgetsBundle.message("compare.to.equality.convert.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(PsiTreeUtil.getParentOfType(element, false, PsiBinaryExpression.class) instanceof PsiBinaryExpression expression)) return;
      final boolean negated = expression.getOperationTokenType().equals(JavaTokenType.NE);
      final PsiMethodCallExpression call = compareToComparedWithZero(expression);
      if (call == null) return;
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(methodExpression);
      if (qualifier == null) return;
      final PsiExpression argument = call.getArgumentList().getExpressions()[0];
      final CommentTracker ct = new CommentTracker();
      final StringBuilder replacement = new StringBuilder();
      if (myReplaceWithEquality) {
        replacement.append(ct.text(qualifier)).append(negated ? "!=" : "==").append(ct.text(argument));
      }
      else {
        if (negated) replacement.append('!');
        if (methodExpression.getQualifierExpression() != null || !(qualifier instanceof PsiThisExpression t) || t.getQualifier() != null) {
          replacement.append(ct.text(qualifier)).append('.');
        }
        replacement.append("equals(").append(ct.text(argument)).append(')');
      }
      ct.replaceAndRestoreComments(expression, replacement.toString());
    }
  }
}
