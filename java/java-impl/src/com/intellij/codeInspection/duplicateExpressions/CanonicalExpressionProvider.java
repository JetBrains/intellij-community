// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

final class CanonicalExpressionProvider {

  private final Map<PsiMethodCallExpression, PsiMethodCallExpression> myCache =
    CollectionFactory.createCustomHashingStrategyMap(new ExpressionHashingStrategy());

  @Contract("null -> null")
  PsiExpression getCanonicalExpression(@Nullable PsiExpression expression) {
    if (expression instanceof PsiMethodCallExpression) {
      return getCanonicalExpression((PsiMethodCallExpression)expression);
    }
    return expression;
  }

  @NotNull
  private PsiMethodCallExpression getCanonicalExpression(@NotNull PsiMethodCallExpression call) {
    return myCache.computeIfAbsent(call, __ -> {
      CallProvider callProvider = ContainerUtil.find(CallProvider.values(), provider -> provider.matches(call));
      if (callProvider == null) return call;
      return callProvider.canonicalCall(call);
    });
  }

  private enum CallProvider {

    PATH_OF(CallMatcher.staticCall("java.nio.file.Paths", "get")) {
      @Override
      @NotNull PsiMethodCallExpression canonicalCall(@NotNull PsiMethodCallExpression call) {
        if (!PsiUtil.isLanguageLevel11OrHigher(call)) return call;
        Project project = call.getProject();
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
        String pathCall = "java.nio.file.Path.of" + call.getArgumentList().getText();
        return (PsiMethodCallExpression)codeStyleManager.shortenClassReferences(factory.createExpressionFromText(pathCall, call));
      }
    };

    private final CallMatcher myCallMatcher;

    CallProvider(CallMatcher callMatcher) {
      myCallMatcher = callMatcher;
    }

    private boolean matches(@NotNull PsiMethodCallExpression call) {
      return myCallMatcher.matches(call);
    }

    @NotNull
    abstract PsiMethodCallExpression canonicalCall(@NotNull PsiMethodCallExpression call);
  }
}
