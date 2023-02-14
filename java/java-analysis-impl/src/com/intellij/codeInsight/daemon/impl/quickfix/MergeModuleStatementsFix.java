// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class MergeModuleStatementsFix<T extends PsiStatement> extends LocalQuickFixAndIntentionActionOnPsiElement {
  protected MergeModuleStatementsFix(@NotNull PsiJavaModule javaModule) {
    super(javaModule);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return PsiUtil.isLanguageLevel9OrHigher(file);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (startElement instanceof PsiJavaModule javaModule) {
      final List<T> statementsToMerge = getStatementsToMerge(javaModule);
      LOG.assertTrue(!statementsToMerge.isEmpty());

      final String text = getReplacementText(statementsToMerge);
      final PsiStatement replacement = JavaPsiFacade.getElementFactory(project).createModuleStatementFromText(text, null);

      final T firstStatement = statementsToMerge.get(0);
      final CommentTracker commentTracker = new CommentTracker();
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
      final PsiElement resultingStatement = codeStyleManager.reformat(commentTracker.replace(firstStatement, replacement));

      for (int i = 1; i < statementsToMerge.size(); i++) {
        T statement = statementsToMerge.get(i);
        commentTracker.delete(statement);
      }
      commentTracker.insertCommentsBefore(resultingStatement);

      if (editor != null) {
        final int offset = resultingStatement.getTextRange().getEndOffset();
        editor.getCaretModel().moveToOffset(offset);
      }
    }
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
