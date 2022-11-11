// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceExpressionAction extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final String myReplacement;
  private final String myPresentation;
  private final String myOrigText;

  public ReplaceExpressionAction(@NotNull PsiExpression expression, @NotNull String replacement, @NotNull String presentation) {
    super(expression);
    myOrigText = expression.getText();
    myReplacement = replacement;
    myPresentation = presentation;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    new CommentTracker().replaceAndRestoreComments(startElement, myReplacement);
  }

  @Override
  public @NotNull String getText() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", myOrigText, myPresentation);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.replace.with.expression");
  }
}
