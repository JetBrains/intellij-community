// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class JavaWithBlockSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return "{ }";
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0) return;

    String text = "{\n}";
    PsiBlockStatement blockStatement = (PsiBlockStatement)factory.createStatementFromText(text, null);
    blockStatement = (PsiBlockStatement)codeStyleManager.reformat(blockStatement);

    blockStatement = (PsiBlockStatement)addAfter(blockStatement, container, statements);

    PsiCodeBlock body = blockStatement.getCodeBlock();
    SurroundWithUtil.indentCommentIfNecessary(body, statements);
    addRangeWithinContainer(body, container, statements, true);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiElement firstChild = blockStatement.getFirstChild();
    if (firstChild != null) {
      TextRange range = firstChild.getTextRange();
      updater.select(TextRange.from(range.getEndOffset(), 0));
    }
  }
}