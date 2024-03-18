// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.asserttoif;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class IfCanBeAssertionInspection extends BaseInspection {
  private static final CallMatcher.Simple MATCHER = CallMatcher.staticCall("com.google.common.base.Preconditions", "checkNotNull");

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfToAssertionVisitor();
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    boolean isObjectsRequireNonNullAvailable = (boolean)infos[0];
    boolean isIfStatement = (boolean)infos[1];
    List<LocalQuickFix> fixes = new ArrayList<>(2);
    if (isObjectsRequireNonNullAvailable) {
      fixes.add(new ReplaceWithObjectsNonNullFix(isIfStatement));
    }
    if (isIfStatement) {
      fixes.add(new IfToAssertionFix());
    }
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  static PsiNewExpression getThrownNewException(PsiElement element) {
    if (element instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)element).getCodeBlock().getStatements();
      if (statements.length == 1) {
        return getThrownNewException(statements[0]);
      }
    }
    else if (element instanceof PsiThrowStatement throwStatement) {
      final PsiExpression exception = PsiUtil.skipParenthesizedExprDown(throwStatement.getException());
      if (exception instanceof PsiNewExpression) {
        return (PsiNewExpression)exception;
      }
    }
    return null;
  }

  private static class IfToAssertionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(statement.getCondition());
      if (condition == null || statement.getElseBranch() != null || getThrownNewException(statement.getThenBranch()) == null) {
        return;
      }
      final boolean isObjectsRequireNonNullAvailable = PsiUtil.isLanguageLevel7OrHigher(statement) &&
                                                       ComparisonUtils.isNullComparison(condition) &&
                                                       ((PsiBinaryExpression)condition).getOperationTokenType() == JavaTokenType.EQEQ;
      registerStatementError(statement, isObjectsRequireNonNullAvailable, true);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (MATCHER.test(expression) && expression.getArgumentList().getExpressionCount() <= 2) { // for parametrized messages we don't suggest anything
        registerMethodCallError(expression, PsiUtil.isLanguageLevel7OrHigher(expression), false);
      }
    }
  }

  private static class ReplaceWithObjectsNonNullFix extends PsiUpdateModCommandQuickFix {
    private final boolean myIsIfStatement;

    ReplaceWithObjectsNonNullFix(boolean isIfStatement) {
      myIsIfStatement = isIfStatement;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      if (myIsIfStatement) {
        final PsiElement parent = startElement.getParent();
        if (!(parent instanceof PsiIfStatement ifStatement)) return;
        final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
        if (!(condition instanceof PsiBinaryExpression)) return;
        PsiExpression nullComparedExpression = ExpressionUtils.getValueComparedWithNull((PsiBinaryExpression)condition);
        if (nullComparedExpression == null) return;
        PsiNewExpression exception = getThrownNewException(ifStatement.getThenBranch());
        if (exception == null) return;
        PsiExpressionList args = exception.getArgumentList();
        PsiExpression message = null;
        if (args != null) {
          PsiExpression arg = ArrayUtil.getFirstElement(args.getExpressions());
          if (arg != null && TypeUtils.isJavaLangString(arg.getType())) {
            message = arg;
          }
        }
        CommentTracker tracker = new CommentTracker();
        final String text = buildNewExpressionText(tracker.markUnchanged(nullComparedExpression), message);
        PsiReplacementUtil.replaceStatementAndShortenClassNames(ifStatement, text + ";", tracker);
      } else {
        PsiReferenceExpression ref = ObjectUtils.tryCast(startElement.getParent(), PsiReferenceExpression.class);
        if (ref == null) return;
        PsiMethodCallExpression methodCall = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
        if (!MATCHER.test(methodCall)) {
          return;
        }
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        if (args.length > 2) return;
        CommentTracker tracker = new CommentTracker();
        final String text = buildNewExpressionText(tracker.markUnchanged(args[0]), (args.length == 2) ? tracker.markUnchanged(args[1]) : null);
        PsiReplacementUtil.replaceExpressionAndShorten(methodCall, text, tracker);
      }
    }

    private static String buildNewExpressionText(@NotNull PsiExpression nullComparedExpression, @Nullable PsiExpression message) {
      final @NonNls StringBuilder result = new StringBuilder("java.util.Objects.requireNonNull(");
      result.append(nullComparedExpression.getText());
      if (message != null) {
        result.append(", ");
        if (ExpressionUtils.hasStringType(message)) {
          result.append(message.getText());
        }
        else {
          result.append(CommonClassNames.JAVA_LANG_STRING + ".valueOf(").append(message.getText()).append(")");
        }
      }
      result.append(")");
      return result.toString();
    }
  }

  private static class IfToAssertionFix extends PsiUpdateModCommandQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.assertion.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = startElement.getParent();
      if (!(parent instanceof PsiIfStatement ifStatement)) {
        return;
      }
      @NonNls final StringBuilder newStatementText = new StringBuilder("assert ");
      CommentTracker tracker = new CommentTracker();
      newStatementText.append(BoolUtils.getNegatedExpressionText(ifStatement.getCondition(), tracker));
      final PsiNewExpression newException = getThrownNewException(ifStatement.getThenBranch());
      final String message = getExceptionMessage(newException, tracker);
      if (message != null) {
        newStatementText.append(':').append(message);
      }
      newStatementText.append(';');
      PsiReplacementUtil.replaceStatement(ifStatement, newStatementText.toString(), tracker);
    }

    private static String getExceptionMessage(PsiNewExpression newExpression, CommentTracker tracker) {
      if (newExpression == null) {
        return null;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return null;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length < 1) {
        return null;
      }
      return tracker.text(arguments[0]);
    }
  }
}
