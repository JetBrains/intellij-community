// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class CallToSuspiciousStringMethodInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.suspicious.string.method.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToSuspiciousStringMethodVisitor();
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)infos[0];
    final List<LocalQuickFix> result = new ArrayList<>();
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiModifierListOwner annotatableQualifier = NonNlsUtils.getAnnotatableQualifier(methodExpression);
    if (annotatableQualifier != null) {
      final LocalQuickFix fix = LocalQuickFix.from(new AddAnnotationModCommandAction(AnnotationUtil.NON_NLS, annotatableQualifier));
      result.add(fix);
    }
    final PsiModifierListOwner annotatableArgument = NonNlsUtils.getAnnotatableArgument(methodCallExpression);
    if (annotatableArgument != null) {
      final LocalQuickFix fix = LocalQuickFix.from(new AddAnnotationModCommandAction(AnnotationUtil.NON_NLS, annotatableArgument));
      result.add(fix);
    }
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static class CallToSuspiciousStringMethodVisitor extends BaseInspectionVisitor {

    private static final CallMatcher SUSPICIOUS_METHODS = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING,
                                                                                   HardcodedMethodConstants.EQUALS,
                                                                                   HardcodedMethodConstants.EQUALS_IGNORE_CASE,
                                                                                   HardcodedMethodConstants.COMPARE_TO,
                                                                                   "compareToIgnoreCase").parameterCount(1);
    private static final CallMatcher TRIM = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "trim").parameterCount(0);

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (SUSPICIOUS_METHODS.test(expression)) {
        final PsiExpressionList argumentList = expression.getArgumentList();
        final PsiExpression[] arguments = argumentList.getExpressions();
        if (arguments.length != 1 || NonNlsUtils.isNonNlsAnnotated(arguments[0])) {
          return;
        }
      }
      else if (!TRIM.test(expression)) {
        return;
      }

      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (NonNlsUtils.isNonNlsAnnotated(qualifier)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }
  }
}
