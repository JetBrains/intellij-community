// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiYieldStatement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.style.ConditionalModel;
import com.siyeh.ig.style.IfConditionalModel;
import com.siyeh.ig.style.SimplifiableIfStatementInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Inspection that detects ternary expressions and if statements using {@code Comparator.compare()} that can be
 * replaced with {@code Comparator.max()} or {@code Comparator.min()} methods available since Java 26.
 * <p>
 * Detected ternary patterns (where {@code comp} is a {@code Comparator} instance):
 * <ul>
 *   <li>{@code comp.compare(a, b) > 0 ? a : b} &rarr; {@code comp.max(a, b)}</li>
 *   <li>{@code comp.compare(a, b) > 0 ? b : a} &rarr; {@code comp.min(a, b)}</li>
 *   <li>{@code comp.compare(a, b) < 0 ? a : b} &rarr; {@code comp.min(a, b)}</li>
 *   <li>{@code comp.compare(a, b) < 0 ? b : a} &rarr; {@code comp.max(a, b)}</li>
 *   <li>{@code comp.compare(a, b) >= 0 ? a : b} &rarr; {@code comp.max(a, b)}</li>
 *   <li>{@code comp.compare(a, b) >= 0 ? b : a} &rarr; {@code comp.min(a, b)}</li>
 *   <li>{@code comp.compare(a, b) <= 0 ? a : b} &rarr; {@code comp.min(a, b)}</li>
 *   <li>{@code comp.compare(a, b) <= 0 ? b : a} &rarr; {@code comp.max(a, b)}</li>
 * </ul>
 * Detected if-statement patterns:
 * <ul>
 *   <li>{@code if (comp.compare(a, b) > 0) return a; else return b;} &rarr; {@code return comp.max(a, b);}</li>
 *   <li>{@code if (comp.compare(a, b) > 0) return a; return b;} &rarr; {@code return comp.max(a, b);}</li>
 *   <li>{@code if (comp.compare(a, b) > 0) x = a; else x = b;} &rarr; {@code x = comp.max(a, b);}</li>
 *   <li>{@code T x = b; if (comp.compare(a, b) > 0) x = a;} &rarr; {@code T x = comp.max(a, b);}</li>
 * </ul>
 * All comparison operators ({@code >}, {@code <}, {@code >=}, {@code <=}) are supported, as well as
 * reversed comparisons (e.g. {@code 0 < comp.compare(a, b)}).
 */
public final class ComparatorMinMaxCanBeUsedInspection extends BaseInspection {

  private static final CallMatcher COMPARATOR_COMPARE =
    CallMatcher.instanceCall("java.util.Comparator", "compare").parameterCount(2);

  @Override
  public @NotNull Set<JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.COMPARATOR_MIN_MAX);
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    String methodName = (String)infos[0];
    return CommonQuickFixBundle.message("fix.can.replace.with.x", "Comparator." + methodName + "()");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    String methodName = (String)infos[0];
    return new ReplaceWithComparatorMinMaxFix(methodName);
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new ComparatorMinMaxVisitor();
  }

  private record MinMaxInfo(
    @NotNull PsiExpression comparatorQualifier,
    @NotNull PsiExpression firstArg,
    @NotNull PsiExpression secondArg,
    @NotNull String methodName
  ) {}
  
  private static @Nullable MinMaxInfo extractMinMaxInfo(@NotNull ConditionalModel conditional) {
    PsiExpression condition = PsiUtil.skipParenthesizedExprDown(conditional.getCondition());
    if (!(condition instanceof PsiBinaryExpression binExpr)) return null;

    PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binExpr.getLOperand());
    PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binExpr.getROperand());
    IElementType tokenType = binExpr.getOperationTokenType();

    PsiMethodCallExpression compareCall;
    RelationType relationType = DfaPsiUtil.getRelationByToken(tokenType);
    if (relationType == null) return null;
    if (lhs instanceof PsiMethodCallExpression lhsCall && COMPARATOR_COMPARE.test(lhsCall) && ExpressionUtils.isZero(rhs)) {
      compareCall = lhsCall;
    }
    else if (rhs instanceof PsiMethodCallExpression rhsCall && COMPARATOR_COMPARE.test(rhsCall) && ExpressionUtils.isZero(lhs)) {
      compareCall = rhsCall;
      relationType = relationType.getFlipped();
    }
    else {
      return null;
    }

    if (relationType == null || relationType == RelationType.NE || !relationType.isInequality()) {
      return null;
    }

    PsiExpression qualifier = compareCall.getMethodExpression().getQualifierExpression();
    if (qualifier == null) return null;

    PsiExpression[] args = compareCall.getArgumentList().getExpressions();
    if (args.length != 2) return null;

    if (SideEffectChecker.mayHaveSideEffects(args[0]) || SideEffectChecker.mayHaveSideEffects(args[1])) {
      return null;
    }

    PsiExpression thenExpr = PsiUtil.skipParenthesizedExprDown(conditional.getThenExpression());
    PsiExpression elseExpr = PsiUtil.skipParenthesizedExprDown(conditional.getElseExpression());
    if (thenExpr == null || elseExpr == null) return null;

    PsiExpression compareFirst = PsiUtil.skipParenthesizedExprDown(args[0]);
    PsiExpression compareSecond = PsiUtil.skipParenthesizedExprDown(args[1]);
    if (compareFirst == null || compareSecond == null) return null;

    EquivalenceChecker checker = EquivalenceChecker.getCanonicalPsiEquivalence();
    boolean thenIsFirst = checker.expressionsAreEquivalent(thenExpr, compareFirst) &&
                          checker.expressionsAreEquivalent(elseExpr, compareSecond);
    boolean thenIsSecond = !thenIsFirst &&
                           checker.expressionsAreEquivalent(thenExpr, compareSecond) &&
                           checker.expressionsAreEquivalent(elseExpr, compareFirst);

    if (!thenIsFirst && !thenIsSecond) return null;

    String methodName = RelationType.GE.isSubRelation(relationType) == thenIsFirst ? "max" : "min";
    // Need to flip arguments to preserve the semantics precisely if equality is not included into condition,
    // as for equal `a` and `b`, `compare(a, b) > 0 ? a : b` returns `b` while `max(a, b)` return `a`.
    boolean flip = !relationType.isSubRelation(RelationType.EQ);

    return new MinMaxInfo(qualifier, args[flip ? 1 : 0], args[flip ? 0 : 1], methodName);
  }

  private static class ComparatorMinMaxVisitor extends BaseInspectionVisitor {
    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      ConditionalModel model = ConditionalModel.from(expression);
      if (model == null) return;
      MinMaxInfo info = extractMinMaxInfo(model);
      if (info == null) return;
      registerError(expression, info.methodName());
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      ConditionalModel model = IfConditionalModel.from(statement, false);
      if (model != null) {
        MinMaxInfo info = extractMinMaxInfo(model);
        if (info == null) return;
        registerError(statement.getFirstChild(), info.methodName());
      }
    }
  }

  private static class ReplaceWithComparatorMinMaxFix extends PsiUpdateModCommandQuickFix {
    private final String myMethodName;

    ReplaceWithComparatorMinMaxFix(@NotNull String methodName) {
      myMethodName = methodName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Comparator." + myMethodName + "()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = PsiTreeUtil.getParentOfType(element, false, PsiConditionalExpression.class, PsiIfStatement.class);
      ConditionalModel model = switch (parent) {
        case PsiConditionalExpression conditional -> ConditionalModel.from(conditional);
        case PsiIfStatement statement -> IfConditionalModel.from(statement, false);
        case null, default -> null;
      };
      if (model == null) return;
      MinMaxInfo info = extractMinMaxInfo(model);
      if (info == null) return;

      CommentTracker ct = new CommentTracker();
      String callText = ct.text(info.comparatorQualifier()) +
                        "." + info.methodName() + "(" +
                        ct.text(info.firstArg()) + ", " +
                        ct.text(info.secondArg()) + ")";

      if (parent instanceof PsiIfStatement ifStatement && model instanceof IfConditionalModel ifModel) {
        replaceIfStatement(ct, ifStatement, ifModel, callText);
      }
      else {
        ct.replaceAndRestoreComments(parent, callText);
      }
    }

    private static void replaceIfStatement(@NotNull CommentTracker ct,
                                           @NotNull PsiIfStatement ifStatement,
                                           @NotNull IfConditionalModel model,
                                           @NotNull String callText) {
      PsiStatement thenBranch = model.getThenBranch();
      PsiStatement elseBranch = model.getElseBranch();
      boolean elseIsOutside = !PsiTreeUtil.isAncestor(ifStatement, elseBranch, true);

      if (elseIsOutside && elseBranch instanceof PsiDeclarationStatement statement) {
        PsiLocalVariable var = (PsiLocalVariable)statement.getDeclaredElements()[0];
        PsiExpression initializer = var.getInitializer();
        if (initializer != null) {
          ct.replace(initializer, callText);
        }
        ct.deleteAndRestoreComments(ifStatement);
      }
      else {
        if (elseIsOutside) {
          ct.delete(elseBranch);
        }
        String statementText = buildStatementText(ct, thenBranch, model.getThenExpression(), callText);
        PsiElement result = ct.replaceAndRestoreComments(ifStatement, statementText);
        SimplifiableIfStatementInspection.tryJoinDeclaration(result);
      }
    }

    private static @NotNull String buildStatementText(@NotNull CommentTracker ct,
                                                      @NotNull PsiStatement thenBranch,
                                                      @NotNull PsiExpression thenExpression,
                                                      @NotNull String callText) {
      if (thenBranch instanceof PsiReturnStatement) {
        return "return " + callText + ";";
      }
      if (thenBranch instanceof PsiYieldStatement) {
        return "yield " + callText + ";";
      }
      if (thenBranch instanceof PsiExpressionStatement exprStmt &&
          exprStmt.getExpression() instanceof PsiAssignmentExpression assignment) {
        return ct.text(assignment.getLExpression()) + " = " + callText + ";";
      }
      // Fallback for method call pattern or other cases
      ct.replace(thenExpression, callText);
      return thenBranch.getText();
    }
  }
}
