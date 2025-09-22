// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.EditDistance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ChangeToSimilarKeywordFix extends PsiUpdateModCommandAction<PsiElement> {

  @NotNull
  private final String myKeyword;

  private ChangeToSimilarKeywordFix(@NotNull PsiElement old, @NotNull String keyword) {
    super(old);
    myKeyword = keyword;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    return Presentation.of(QuickFixBundle.message("change.to.similar.keyword.fix.name", element.getText(), myKeyword));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.to.similar.keyword.fix.family.name");
  }

  @Override
  protected void invoke(@NotNull ActionContext context,
                        @NotNull PsiElement element,
                        @NotNull ModPsiUpdater updater) {
    PsiFile file = element.getContainingFile();
    if (file == null) return;
    Document document = file.getFileDocument();
    TextRange textRange = element.getTextRange();
    document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), myKeyword);
  }


  /**
   * Creates a fix if the new keywords are similar enough.
   */
  @Nullable
  public static ChangeToSimilarKeywordFix createFix(@Nullable PsiElement old, @NotNull Collection<@NotNull String> newKeywords) {
    if (old == null || newKeywords.isEmpty()) return null;
    String oldText = old.getText();
    int minDistance = Integer.MAX_VALUE;
    String bestKeyword = null;
    int maxLevenshtein;
    if (oldText.length() <= 4) {
      maxLevenshtein = 1;
    }
    else {
      maxLevenshtein = 2;
    }
    for (String keyword : newKeywords) {
      int levenshtein = EditDistance.optimalAlignment(keyword, oldText, true, maxLevenshtein + 2);
      if (levenshtein < minDistance) {
        minDistance = levenshtein;
        bestKeyword = keyword;
      }
    }
    if (maxLevenshtein < minDistance) {
      return null;
    }
    return new ChangeToSimilarKeywordFix(old, bestKeyword);
  }
}
