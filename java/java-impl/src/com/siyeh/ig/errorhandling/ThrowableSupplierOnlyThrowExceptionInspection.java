// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.ExceptionUtil.HandlePlace.UNHANDLED;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER;

public final class ThrowableSupplierOnlyThrowExceptionInspection extends BaseInspection {
  private static final CallMatcher OPTIONAL_OR_ELSE_THROW = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalDouble", "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalInt", "orElseThrow").parameterCount(1),
    CallMatcher.instanceCall("java.util.OptionalLong", "orElseThrow").parameterCount(1)
  );

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    return new LocalQuickFix[]{new ThrowToReturnQuickFix()};
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowableSupplierOnlyThrowExceptionVisitor();
  }

  private static class ThrowToReturnQuickFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(startElement, PsiMethodCallExpression.class);
      if (callExpression == null) {
        return;
      }
      PsiLambdaExpression psiLambdaExpression = getLambdaSupplier(callExpression);
      if (psiLambdaExpression == null) return;
      List<PsiThrowStatement> throwStatements = getThrowStatements(psiLambdaExpression);

      PsiElement returnStatement = null;
      for (PsiThrowStatement throwStatement : throwStatements) {
        CommentTracker tracker = new CommentTracker();
        StringBuilder builder = new StringBuilder();
        for (PsiElement child : throwStatement.getChildren()) {
          if (child instanceof PsiKeyword keyword && keyword.getTokenType() == JavaTokenType.THROW_KEYWORD) {
            builder.append("return");
          }
          else {
            builder.append(child.getText());
            tracker.grabComments(child);
          }
        }

        returnStatement = tracker.replaceAndRestoreComments(throwStatement, builder.toString());
      }
      if (returnStatement == null) {
        return;
      }
      PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(returnStatement, PsiLambdaExpression.class);
      if (lambdaExpression != null) {
        final PsiElement body = lambdaExpression.getBody();
        if (body != null) {
          PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
          if (expression != null) {
            body.replace(expression);
          }
        }
      }
    }
  }

  private static @Nullable PsiLambdaExpression getLambdaSupplier(@NotNull PsiMethodCallExpression expression) {
    PsiExpression[] expressions = expression.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return null;
    }
    PsiExpression supplier = expressions[0];
    if (!(supplier instanceof PsiLambdaExpression lambdaSupplier)) {
      return null;
    }
    return lambdaSupplier;
  }

  @Unmodifiable
  private static @NotNull List<PsiThrowStatement> getThrowStatements(@Nullable PsiLambdaExpression psiLambdaExpression) {
    if (psiLambdaExpression == null) {
      return List.of();
    }
    PsiElement lambdaSupplierBody = psiLambdaExpression.getBody();
    if (lambdaSupplierBody == null) {
      return List.of();
    }

    Collection<PsiThrowStatement> statements =
      ContainerUtil.filter(PsiTreeUtil.collectElementsOfType(lambdaSupplierBody, PsiThrowStatement.class),
                           e -> e.getException() != null &&
                                e.getException().getType() instanceof PsiClassType exceptionType &&
                                ExceptionUtil.getHandlePlace(e, exceptionType, lambdaSupplierBody) == UNHANDLED);

    return ContainerUtil.findAll(statements, statement ->
      PsiTreeUtil.skipParentsOfType(statement, PsiCodeBlock.class, PsiStatement.class) == psiLambdaExpression);
  }

  private static class ThrowableSupplierOnlyThrowExceptionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!OPTIONAL_OR_ELSE_THROW.test(expression)) {
        return;
      }
      PsiLambdaExpression lambdaSupplier = getLambdaSupplier(expression);
      if (lambdaSupplier == null) return;
      PsiType lambdaType = lambdaSupplier.getFunctionalInterfaceType();
      if (lambdaType == null ||
          !InheritanceUtil.isInheritor(lambdaType, JAVA_UTIL_FUNCTION_SUPPLIER) ||
          !(lambdaType instanceof PsiClassType classType && classType.getParameterCount() == 1) ||
          !InheritanceUtil.isInheritor(classType.getParameters()[0], CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }

      PsiElement lambdaSupplierBody = lambdaSupplier.getBody();
      if (lambdaSupplierBody == null || !ControlFlowUtils.lambdaExpressionAlwaysThrowsException(lambdaSupplier)) {
        return;
      }

      List<PsiThrowStatement> all = getThrowStatements(lambdaSupplier);
      if (all.size() == 0) {
        return;
      }
      PsiIdentifier[] identifiers = PsiTreeUtil.getChildrenOfType(expression.getMethodExpression(), PsiIdentifier.class);
      if (identifiers == null || identifiers.length != 1) {
        return;
      }
      registerError(identifiers[0]);
    }
  }
}
