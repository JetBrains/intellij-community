// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AddFinallyFix extends PsiUpdateModCommandAction<PsiTryStatement> {
  public AddFinallyFix(PsiTryStatement statement) {
    super(statement);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiTryStatement tryStatement, @NotNull ModPsiUpdater updater) {
    if (tryStatement.getFinallyBlock() != null) return;
    PsiStatement replacement =
      JavaPsiFacade.getElementFactory(context.project())
        .createStatementFromText(tryStatement.getText() + "\nfinally {\n\n}", tryStatement);
    PsiTryStatement result = (PsiTryStatement)tryStatement.replace(replacement);
    moveCaretToFinallyBlock(updater, Objects.requireNonNull(result.getFinallyBlock()));
  }

  private static void moveCaretToFinallyBlock(@NotNull ModPsiUpdater updater, @NotNull PsiCodeBlock block) {
    PsiFile psiFile = block.getContainingFile();
    Document document = psiFile.getViewProvider().getDocument();
    Project project = psiFile.getProject();
    updater.moveCaretTo(Objects.requireNonNull(block.getRBrace()));
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    TextRange finallyBlockRange = block.getTextRange();
    int newLineOffset = finallyBlockRange.getStartOffset() + 2;
    CodeStyleManager.getInstance(project).adjustLineIndent(document, newLineOffset);
    int lineEndOffset = document.getLineEndOffset(document.getLineNumber(updater.getCaretOffset()) - 1);
    updater.moveCaretTo(lineEndOffset);
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.finally.block.family");
  }
}
