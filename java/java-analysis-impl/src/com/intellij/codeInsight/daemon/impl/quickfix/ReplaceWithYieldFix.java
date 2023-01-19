// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithYieldFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  public ReplaceWithYieldFix(@NotNull PsiReturnStatement statement) {
    super(statement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiReturnStatement returnStatement = (PsiReturnStatement)startElement;
    PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return;
    }
    TextRange range = returnStatement.getFirstChild().getTextRange();
    // Work on document level to preserve formatting
    file.getViewProvider().getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), PsiKeyword.YIELD);
  }

  @Override
  public @NotNull String getText() {
    return CommonQuickFixBundle.message("fix.replace.with.x", PsiKeyword.YIELD);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }
}
