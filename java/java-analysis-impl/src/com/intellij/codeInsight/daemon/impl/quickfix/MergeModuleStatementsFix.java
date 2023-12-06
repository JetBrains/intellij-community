// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class MergeModuleStatementsFix<T extends PsiStatement> extends PsiUpdateModCommandAction<PsiJavaModule> {
  protected static final Logger LOG = Logger.getInstance(MergeModuleStatementsFix.class);
  protected MergeModuleStatementsFix(@NotNull PsiJavaModule javaModule) {
    super(javaModule);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaModule element) {
    return HighlightingFeature.MODULES.isAvailable(element) ? Presentation.of(getText()) : null;
  }

  @IntentionName
  abstract @NotNull String getText();

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaModule javaModule, @NotNull ModPsiUpdater updater) {
    final List<T> statementsToMerge = getStatementsToMerge(javaModule);
    LOG.assertTrue(!statementsToMerge.isEmpty());

    final String text = getReplacementText(statementsToMerge);
    final PsiStatement replacement = JavaPsiFacade.getElementFactory(context.project()).createModuleStatementFromText(text, null);

    final T firstStatement = statementsToMerge.get(0);
    final CommentTracker commentTracker = new CommentTracker();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.project());
    final PsiElement resultingStatement = codeStyleManager.reformat(commentTracker.replace(firstStatement, replacement));

    for (int i = 1; i < statementsToMerge.size(); i++) {
      T statement = statementsToMerge.get(i);
      commentTracker.delete(statement);
    }
    commentTracker.insertCommentsBefore(resultingStatement);

    updater.moveTo(resultingStatement.getTextRange().getEndOffset());
  }

  @NotNull
  protected abstract String getReplacementText(List<? extends T> statementsToMerge);

  @NotNull
  protected abstract List<T> getStatementsToMerge(@NotNull PsiJavaModule javaModule);

  @NotNull
  protected static String joinUniqueNames(@NotNull List<String> names) {
    final Set<String> unique = new HashSet<>();
    return names.stream()
      .filter(name -> unique.add(name))
      .collect(Collectors.joining(","));
  }

  @Nullable
  public static MergeModuleStatementsFix<?> createFix(@Nullable PsiElement statement) {
    if (statement instanceof PsiPackageAccessibilityStatement) {
      return MergePackageAccessibilityStatementsFix.createFix((PsiPackageAccessibilityStatement)statement);
    }
    else if (statement instanceof PsiProvidesStatement) {
      return MergeProvidesStatementsFix.createFix((PsiProvidesStatement)statement);
    }
    return null;
  }
}
