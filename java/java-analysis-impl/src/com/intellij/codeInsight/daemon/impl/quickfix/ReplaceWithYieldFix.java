// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReturnStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithYieldFix extends PsiUpdateModCommandAction<PsiReturnStatement> {
  public ReplaceWithYieldFix(@NotNull PsiReturnStatement statement) {
    super(statement);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReturnStatement returnStatement, @NotNull ModPsiUpdater updater) {
    PsiExpression returnValue = returnStatement.getReturnValue();
    if (returnValue == null) {
      return;
    }
    TextRange range = returnStatement.getFirstChild().getTextRange();
    // Work on document level to preserve formatting
    Document document = returnStatement.getContainingFile().getViewProvider().getDocument();
    document.replaceString(range.getStartOffset(), range.getEndOffset(), JavaKeywords.YIELD);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReturnStatement element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", JavaKeywords.YIELD);
  }
}
