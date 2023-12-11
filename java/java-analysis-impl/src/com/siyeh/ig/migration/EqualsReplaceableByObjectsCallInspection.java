// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Bas Leijdekkers
 */
public final class EqualsReplaceableByObjectsCallInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public boolean checkNotNull;

  private static final EquivalenceChecker EQUIVALENCE = new NoSideEffectExpressionEquivalenceChecker();

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("checkNotNull", InspectionGadgetsBundle.message("equals.replaceable.by.objects.check.not.null.option")));
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.problem.descriptor");
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new EqualsReplaceableByObjectsCallFix((String)infos[0], (String)infos[1], (Boolean)infos[2]);
  }

  private static class EqualsReplaceableByObjectsCallFix extends PsiUpdateModCommandQuickFix {

    private final String myName1;
    private final String myName2;
    private final Boolean myEquals;

    EqualsReplaceableByObjectsCallFix(String name1, String name2, Boolean equals) {
      myName1 = name1;
      myName2 = name2;
      myEquals = equals;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Objects.equals()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiBinaryExpression ||
            element instanceof PsiMethodCallExpression ||
            element instanceof PsiConditionalExpression)) {
        return;
      }
      final PsiExpression expression = (PsiExpression)element;
      final String expressionText = "java.util.Objects.equals(" + myName1 + "," + myName2 + ")";
      PsiReplacementUtil.replaceExpressionAndShorten(expression, myEquals ? expressionText : "!" + expressionText, new CommentTracker());
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsReplaceableByObjectsCallVisitor();
  }

  private class EqualsReplaceableByObjectsCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final String methodName = expression.getMethodExpression().getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiExpression qualifierExpression = getQualifierExpression(expression);
      if (qualifierExpression instanceof PsiQualifiedExpression) {
        return;
      }
      if (isNotNullExpressionOrConstant(qualifierExpression)) {
        return;
      }
      final PsiElement parentExpression =
        PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class, PsiPrefixExpression.class);
      if (parentExpression instanceof PsiBinaryExpression) {
        if (processNotNullCheck((PsiBinaryExpression)parentExpression)) {
          return;
        }
      }
      else if (parentExpression instanceof PsiConditionalExpression) {
        if (processNotNullCondition((PsiConditionalExpression)parentExpression)) {
          return;
        }
      }
      if (qualifierExpression == null) {
        return;
      }
      final PsiExpression argumentExpression = getArgumentExpression(expression);
      if (argumentExpression == null) {
        return;
      }
      if (isOnTheFly()) {
        registerError(expression, ProblemHighlightType.INFORMATION, qualifierExpression.getText(), argumentExpression.getText(), true);
      }
    }

    private boolean processNotNullCheck(PsiBinaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      final PsiExpression rightOperand = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        return registerProblem(expression, rightOperand, true);
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        if (rightOperand instanceof PsiPrefixExpression &&
            JavaTokenType.EXCL.equals(((PsiPrefixExpression)rightOperand).getOperationTokenType())) {
          final PsiExpression negatedRightOperand = PsiUtil.skipParenthesizedExprDown(((PsiPrefixExpression)rightOperand).getOperand());
          return registerProblem(expression, negatedRightOperand, false);
        }
      }
      return true;
    }

    /**
     * Report null-safe 'equals' checks in the form of ternary operator:
     * <ul>
     * <li>A == null ? B == null : A.equals(B) ~ equals(A, B)</li>
     * <li>A == null ? B != null : !A.equals(B) ~ !equals(A, B)</li>
     * <li>A != null ? A.equals(B) : B == null ~ equals(A, B)</li>
     * <li>A != null ? !A.equals(B) : B != null ~ !equals(A, B)</li>
     * </ul>
     *
     * @return true if such 'equals' check is found
     */
    private boolean processNotNullCondition(@NotNull PsiConditionalExpression expression) {
      final NullCheck conditionNullCheck = NullCheck.create(expression.getCondition());
      if (conditionNullCheck == null) return false;

      final PsiExpression nullBranch = conditionNullCheck.isEqual ? expression.getThenExpression() : expression.getElseExpression();
      final PsiExpression nonNullBranch = conditionNullCheck.isEqual ? expression.getElseExpression() : expression.getThenExpression();

      final NullCheck otherNullCheck = NullCheck.create(nullBranch);
      final EqualsCheck equalsCheck = EqualsCheck.create(nonNullBranch);
      if (otherNullCheck == null || equalsCheck == null || otherNullCheck.isEqual != equalsCheck.isEqual) return false;

      if (EQUIVALENCE.expressionsAreEquivalent(conditionNullCheck.compared, equalsCheck.qualifier) &&
          EQUIVALENCE.expressionsAreEquivalent(otherNullCheck.compared, equalsCheck.argument)) {
        registerError(expression, equalsCheck.qualifier.getText(), equalsCheck.argument.getText(), Boolean.valueOf(equalsCheck.isEqual));
        return true;
      }

      return false;
    }

    /**
     * Match the patterns, and register the error if a pattern is matched:
     * <pre>
     * x==null || !x.equals(y)
     * x!=null && x.equals(y)</pre>
     *
     * @return true if the pattern is matched
     */
    private boolean registerProblem(@NotNull PsiBinaryExpression expression, PsiExpression rightOperand, boolean equal) {
      if (rightOperand instanceof PsiMethodCallExpression methodCallExpression) {
        final NullCheck nullCheck = NullCheck.create(expression.getLOperand());
        if (nullCheck != null && nullCheck.isEqual != equal) {
          final PsiExpression nullCheckedExpression = nullCheck.compared;
          final PsiExpression qualifierExpression = getQualifierExpression(methodCallExpression);
          if (EQUIVALENCE.expressionsAreEquivalent(qualifierExpression, nullCheckedExpression)) {
            final PsiExpression argumentExpression = getArgumentExpression(methodCallExpression);
            if (argumentExpression != null) {
              final PsiExpression expressionToReplace = checkEqualityBefore(expression, equal, qualifierExpression, argumentExpression);
              ProblemHighlightType highlightType = checkNotNull || expression != expressionToReplace ?
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.INFORMATION;
              if (isOnTheFly() || highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
                registerError(expressionToReplace, highlightType,
                              nullCheckedExpression.getText(), argumentExpression.getText(), Boolean.valueOf(equal));
              }
              return true;
            }
          }
        }
      }
      return false;
    }

    /**
     * Match the left side of the patterns:
     * <pre>
     * x!=y && (x==null || !x.equals(y))
     * x==y || (x!=null && x.equals(y))</pre>
     *
     * @return the expression matching the pattern, or the original expression if there's no match
     */
    @NotNull
    private PsiExpression checkEqualityBefore(@NotNull PsiExpression expression, boolean equal, PsiExpression part1, PsiExpression part2) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiBinaryExpression binaryExpression) {
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (equal && JavaTokenType.OROR.equals(tokenType) || !equal && JavaTokenType.ANDAND.equals(tokenType)) {
          if (PsiTreeUtil.isAncestor(binaryExpression.getROperand(), expression, false)) {
            final PsiExpression lhs = binaryExpression.getLOperand();
            if (isEquality(lhs, equal, part1, part2)) {
              return binaryExpression;
            }
          }
        }
      }
      return expression;
    }

    private static boolean isEquality(PsiExpression expression, boolean equals, PsiExpression part1, PsiExpression part2) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (!(expression instanceof PsiBinaryExpression binaryExpression)) {
        return false;
      }
      if (equals) {
        if (!JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      else {
        if (!JavaTokenType.NE.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      final PsiExpression leftOperand = binaryExpression.getLOperand();
      final PsiExpression rightOperand = binaryExpression.getROperand();
      return EQUIVALENCE.expressionsAreEquivalent(leftOperand, part1) && EQUIVALENCE.expressionsAreEquivalent(rightOperand, part2) ||
             EQUIVALENCE.expressionsAreEquivalent(leftOperand, part2) && EQUIVALENCE.expressionsAreEquivalent(rightOperand, part1);
    }
  }

  private static boolean isNotNullExpressionOrConstant(PsiExpression expression) {
    int preventEndlessLoop = 5;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    while (expression instanceof PsiReferenceExpression) {
      if (--preventEndlessLoop == 0) return false;
      expression = findFinalVariableDefinition((PsiReferenceExpression)expression);
    }
    if (expression instanceof PsiNewExpression ||
        expression instanceof PsiArrayInitializerExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return true;
    }
    return PsiUtil.isConstantExpression(expression);
  }

  @Nullable
  private static PsiExpression findFinalVariableDefinition(@NotNull PsiReferenceExpression expression) {
    final PsiElement resolved = expression.resolve();
    if (resolved instanceof PsiVariable variable) {
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        return PsiUtil.skipParenthesizedExprDown(variable.getInitializer());
      }
    }
    return null;
  }

  private static PsiExpression getArgumentExpression(PsiMethodCallExpression callExpression) {
    final PsiExpression[] expressions = callExpression.getArgumentList().getExpressions();
    return expressions.length == 1 ? PsiUtil.skipParenthesizedExprDown(expressions[0]) : null;
  }

  private static PsiExpression getQualifierExpression(PsiMethodCallExpression expression) {
    return PsiUtil.skipParenthesizedExprDown(expression.getMethodExpression().getQualifierExpression());
  }

  //<editor-fold desc="Helpers">
  private record Negated(@NotNull PsiExpression expression, boolean isEqual) {
    @Nullable
    static Negated create(@Nullable PsiExpression maybeNegatedExpression) {
      boolean equal = true;
      PsiExpression expression = PsiUtil.skipParenthesizedExprDown(maybeNegatedExpression);
      if (expression instanceof PsiPrefixExpression prefixExpression) {
        if (JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          equal = false;
          expression = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
        }
      }
      return expression != null ? new Negated(expression, equal) : null;
    }
  }

  private record NullCheck(@NotNull PsiExpression compared, boolean isEqual) {
    @Nullable
    private static NullCheck create(@Nullable PsiExpression maybeNullCheckExpression) {
      final Negated n = Negated.create(maybeNullCheckExpression);
      if (n != null && n.expression instanceof PsiBinaryExpression binaryExpression) {
        PsiExpression comparedWithNull = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getValueComparedWithNull(binaryExpression));
        if (comparedWithNull != null) {
          boolean equal = JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType());
          return new NullCheck(comparedWithNull, equal == n.isEqual);
        }
      }
      return null;
    }
  }

  private record EqualsCheck(@NotNull PsiExpression argument, @NotNull PsiExpression qualifier, boolean isEqual) {
    @Nullable
    private static EqualsCheck create(@Nullable PsiExpression maybeEqualsCheckExpression) {
      final Negated n = Negated.create(maybeEqualsCheckExpression);
      if (n != null && n.expression instanceof PsiMethodCallExpression callExpression) {
        if (HardcodedMethodConstants.EQUALS.equals(callExpression.getMethodExpression().getReferenceName())) {
          final PsiExpression argument = getArgumentExpression(callExpression);
          final PsiExpression qualifier = getQualifierExpression(callExpression);
          if (argument != null && qualifier != null) {
            return new EqualsCheck(argument, qualifier, n.isEqual);
          }
        }
      }
      return null;
    }
  }

  private static class NoSideEffectExpressionEquivalenceChecker extends EquivalenceChecker {

    @Override
    protected Match newExpressionsMatch(@NotNull PsiNewExpression newExpression1,
                                        @NotNull PsiNewExpression newExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match methodCallExpressionsMatch(@NotNull PsiMethodCallExpression methodCallExpression1,
                                               @NotNull PsiMethodCallExpression methodCallExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match assignmentExpressionsMatch(@NotNull PsiAssignmentExpression assignmentExpression1,
                                               @NotNull PsiAssignmentExpression assignmentExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match arrayInitializerExpressionsMatch(@NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
                                                     @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
      return EXACT_MISMATCH;
    }

    @Override
    protected Match unaryExpressionsMatch(@NotNull PsiUnaryExpression unaryExpression1, @NotNull PsiUnaryExpression unaryExpression2) {
      if (isSideEffectUnaryOperator(unaryExpression1.getOperationTokenType())) {
        return EXACT_MISMATCH;
      }
      return super.unaryExpressionsMatch(unaryExpression1, unaryExpression2);
    }

    private static boolean isSideEffectUnaryOperator(IElementType tokenType) {
      return JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType);
    }
  }
  //</editor-fold>
}
