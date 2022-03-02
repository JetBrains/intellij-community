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
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CanonicalExpressionProvider {

  private static final List<CallMapper<PsiMethodCallExpression>> MAPPERS = List.of(
    new CallMapper<PsiMethodCallExpression>().register(CallMatcher.staticCall("java.nio.file.Paths", "get"), call -> {
      if (!PsiUtil.isLanguageLevel11OrHigher(call)) return call;
      Project project = call.getProject();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      String pathCall = "java.nio.file.Path.of" + call.getArgumentList().getText();
      return (PsiMethodCallExpression)codeStyleManager.shortenClassReferences(factory.createExpressionFromText(pathCall, call));
    })
  );

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
      return MAPPERS.stream().map(m -> m.mapFirst(call)).filter(Objects::nonNull).findFirst().orElse(call);
    });
  }
}
