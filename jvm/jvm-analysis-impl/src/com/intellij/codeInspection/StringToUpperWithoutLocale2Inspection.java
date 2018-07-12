// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.JvmAnalysisBundle;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiIdentifier;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallableReferenceExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;


public class StringToUpperWithoutLocale2Inspection extends AbstractBaseUastLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() { //TODO remove once inspection is registered in JvmAnalysisPlugin.xml
    return "StringToUpperWithoutLocale2Inspection";
  }

  private static final UastCallMatcher MATCHER = UastCallMatcher.anyOf(
    UastCallMatcher.builder()
                   .withMethodName(HardcodedMethodConstants.TO_UPPER_CASE)
                   .withClassFqn(CommonClassNames.JAVA_LANG_STRING)
                   .withArgumentsCount(0).build(),
    UastCallMatcher.builder()
                   .withMethodName(HardcodedMethodConstants.TO_LOWER_CASE)
                   .withClassFqn(CommonClassNames.JAVA_LANG_STRING)
                   .withArgumentsCount(0).build()
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
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
    if (!MATCHER.testCallExpression(callExpression)) return;
    if (NonNlsUastUtil.isCallExpressionWithNonNlsReceiver(callExpression)) return;

    PsiElement methodIdentifierPsi = AnalysisUastUtil.getMethodIdentifierSourcePsi(callExpression);
    if (methodIdentifierPsi == null) return;

    String methodName = callExpression.getMethodName();
    if (methodName == null) return; // shouldn't happen
    holder.registerProblem(methodIdentifierPsi, getErrorDescription(methodName));
  }

  private static void handleCallableReferenceExpression(@NotNull UCallableReferenceExpression expression,
                                                        @NotNull PsiElement identifier,
                                                        @NotNull ProblemsHolder holder) {
    if (!MATCHER.testCallableReferenceExpression(expression)) return;
    if (NonNlsUastUtil.isCallableReferenceExpressionWithNonNlsQualifier(expression)) return;

    holder.registerProblem(identifier, getErrorDescription(expression.getCallableName()));
  }

  @NotNull
  private static String getErrorDescription(@NotNull String methodName) {
    return JvmAnalysisBundle.message("jvm.inspections.string.touppercase.tolowercase.without.locale.description", methodName);
  }
}
