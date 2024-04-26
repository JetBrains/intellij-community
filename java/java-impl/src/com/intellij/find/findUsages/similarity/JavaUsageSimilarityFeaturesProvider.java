// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaUsageSimilarityFeaturesProvider implements UsageSimilarityFeaturesProvider {

  @Override
  @RequiresReadLock
  @RequiresBackgroundThread
  public @NotNull Bag getFeatures(@NotNull PsiElement usage) {
    PsiElement context = getContainingStatement(usage);
    if (context != null) {
      final Bag usageFeatures = new JavaSimilarityFeaturesExtractor(usage, context).getFeatures();
      if (Registry.is("similarity.find.usages.parent.statement.condition.feature")) {
        usageFeatures.addAll(getParentStatementFeatures(usage, context));
      }
      return usageFeatures;
    }
    return Bag.EMPTY_BAG;
  }

  public @Nullable PsiElement getContainingStatement(@NotNull PsiElement element) {
    PsiElement containingStatement = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class,
                                                                 PsiExpressionStatement.class,
                                                                 PsiIfStatement.class,
                                                                 PsiWhileStatement.class,
                                                                 PsiTryStatement.class,
                                                                 PsiThrowStatement.class,
                                                                 PsiSwitchStatement.class,
                                                                 PsiReturnStatement.class,
                                                                 PsiLoopStatement.class,
                                                                 PsiImportStatement.class,
                                                                 PsiForStatement.class,
                                                                 PsiForeachStatement.class,
                                                                 PsiConditionalLoopStatement.class,
                                                                 PsiBlockStatement.class,
                                                                 PsiMethod.class);
    if (containingStatement instanceof PsiDeclarationStatement && containingStatement.getParent() instanceof PsiForStatement) {
      return containingStatement.getParent();
    }
    return containingStatement;
  }

  private static @NotNull Bag getParentStatementFeatures(@NotNull PsiElement usage, @NotNull PsiElement context) {
    final PsiElement parentControlStatement =
      PsiTreeUtil.findFirstParent(context, true,
                                  element -> element instanceof PsiConditionalLoopStatement || element instanceof PsiIfStatement);
    if (parentControlStatement instanceof PsiConditionalLoopStatement) {
      final PsiExpression conditionExpression = ((PsiConditionalLoopStatement)parentControlStatement).getCondition();
      if (conditionExpression != null) {
        return new JavaSimilarityFeaturesExtractor(usage ,conditionExpression).getFeatures();
      }
    }
    if (parentControlStatement instanceof PsiIfStatement) {
      final PsiExpression conditionExpression = ((PsiIfStatement)parentControlStatement).getCondition();
      if (conditionExpression != null) {
        return new JavaSimilarityFeaturesExtractor(usage, conditionExpression).getFeatures();
      }
    }
    return Bag.EMPTY_BAG;
  }
}
