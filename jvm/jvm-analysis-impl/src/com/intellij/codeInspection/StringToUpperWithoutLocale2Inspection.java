// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallableReferenceExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;


public class StringToUpperWithoutLocale2Inspection extends AbstractBaseUastLocalInspectionTool {
  private static final CallMatcher MATCHER = CallMatcher.instanceCall(
    CommonClassNames.JAVA_LANG_STRING, HardcodedMethodConstants.TO_UPPER_CASE, HardcodedMethodConstants.TO_LOWER_CASE
  ).parameterCount(0);

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        UCallExpression callExpression = AnalysisUastUtil.getUCallExpression(element);
        if (callExpression != null) {
          handleCallExpression(callExpression, holder);
          return;
        }

        if (!(element instanceof PsiIdentifier)) return;
        PsiElement parent = element.getParent();
        UElement parentUElement = UastContextKt.toUElement(parent);
        if (parentUElement instanceof UCallableReferenceExpression) {
          handleCallableReferenceExpression((UCallableReferenceExpression)parentUElement, element, holder);
        }
      }
    };
  }

  private static void handleCallExpression(@NotNull UCallExpression callExpression, @NotNull ProblemsHolder holder) {
    if (!MATCHER.uCallMatches(callExpression)) return;
    if (NonNlsUastUtil.isCallExpressionWithNonNlsReceiver(callExpression)) return;

    PsiElement methodIdentifierPsi = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
    if (methodIdentifierPsi == null) return;

    @NlsSafe String methodName = callExpression.getMethodName();
    if (methodName == null) return; // shouldn't happen
    holder.registerProblem(methodIdentifierPsi, getErrorDescription(methodName));
  }

  private static void handleCallableReferenceExpression(@NotNull UCallableReferenceExpression expression,
                                                        @NotNull PsiElement identifier,
                                                        @NotNull ProblemsHolder holder) {
    if (!MATCHER.uCallableReferenceMatches(expression)) return;
    if (NonNlsUastUtil.isCallableReferenceExpressionWithNonNlsQualifier(expression)) return;

    holder.registerProblem(identifier, getErrorDescription(expression.getCallableName()));
  }

  private static @InspectionMessage @NotNull String getErrorDescription(@NotNull String methodName) {
    return JvmAnalysisBundle.message("jvm.inspections.string.touppercase.tolowercase.without.locale.description", methodName);
  }
}
