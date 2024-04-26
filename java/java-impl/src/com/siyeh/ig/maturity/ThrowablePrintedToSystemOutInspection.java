// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_SYSTEM;

/**
 * @author Bas Leijdekkers
 */
public final class ThrowablePrintedToSystemOutInspection extends BaseInspection {

  public ConvertSystemOutToLogCallFix.PopularLogLevel myLogLevel = ConvertSystemOutToLogCallFix.PopularLogLevel.ERROR;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.dropdown(
        "myLogLevel",
        InspectionGadgetsBundle.message("throwable.printed.to.system.out.problem.fix.level.option"),
        ConvertSystemOutToLogCallFix.PopularLogLevel.class,
        level -> level.toMethodName())
        );
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    final String fieldName = (String)infos[0];
    final String methodName = (String)infos[1];
    return InspectionGadgetsBundle.message("throwable.printed.to.system.out.problem.descriptor", fieldName, methodName);
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowablePrintedToSystemOutVisitor();
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    if (infos.length != 3) {
      return null;
    }
    if (!(infos[2] instanceof PsiExpression expression &&
          expression.getParent() instanceof PsiExpressionList expressionList &&
          expressionList.getParent() instanceof PsiMethodCallExpression call)) {
      return null;
    }

    ModCommandAction fix = ConvertSystemOutToLogCallFix.createFix(call, myLogLevel.name().toLowerCase(Locale.ROOT));
    if (fix != null) {
      return ModCommandService.getInstance().wrapToQuickFix(fix);
    }
    return null;
  }

  private static class ThrowablePrintedToSystemOutVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      ExceptionIsPrintedToSystemOutResult result = getExceptionIsPrintedToSystemOutResult(expression);
      if (result == null) return;
      registerError(result.argument(), result.fieldName(), result.methodName(), result.argument());
    }
  }

  public static @Nullable ExceptionIsPrintedToSystemOutResult getExceptionIsPrintedToSystemOutResult(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final @NonNls String methodName = methodExpression.getReferenceName();
    if (!"print".equals(methodName) && !"println".equals(methodName)) {
      return null;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) {
      return null;
    }
    final PsiExpression argument = arguments[0];

    if (!TypeUtils.expressionHasTypeOrSubtype(argument, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return null;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression qualifierReference)) {
      return null;
    }
    final PsiElement target = qualifierReference.resolve();
    if (!(target instanceof PsiField field)) {
      return null;
    }
    final @NonNls String fieldName = field.getName();
    if (!HardcodedMethodConstants.OUT.equals(fieldName) && !HardcodedMethodConstants.ERR.equals(fieldName)) {
      return null;
    }
    final PsiClass aClass = field.getContainingClass();
    if (aClass == null || !JAVA_LANG_SYSTEM.equals(aClass.getQualifiedName())) {
      return null;
    }

    return new ExceptionIsPrintedToSystemOutResult(methodName, argument, fieldName);
  }

  public record ExceptionIsPrintedToSystemOutResult(@NotNull String methodName,
                                                    @NotNull PsiExpression argument,
                                                    @NotNull String fieldName) {
  }
}
