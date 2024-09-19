// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithIfSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.if.template");
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) {
    PsiIfStatement ifStatement = surroundStatements(context.project(), container, statements, "");
    if (ifStatement == null) return;
    ifStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(ifStatement);
    if (ifStatement == null) return;

    final PsiJavaToken lParenth = ifStatement.getLParenth();
    assert lParenth != null;
    final TextRange range = lParenth.getTextRange();
    updater.select(TextRange.from(range.getEndOffset(), 0));
  }

  public PsiIfStatement surroundStatements(Project project, PsiElement container, PsiElement[] statements, String condition) {
    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0) {
      return null;
    }

    @NonNls String text = "if(" + condition + "){\n}";
    PsiIfStatement ifStatement = (PsiIfStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(text, null);
    ifStatement = (PsiIfStatement)CodeStyleManager.getInstance(project).reformat(ifStatement);
    ifStatement = (PsiIfStatement)addAfter(ifStatement, container, statements);

    final PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch != null) {
      PsiCodeBlock thenBlock = ((PsiBlockStatement)thenBranch).getCodeBlock();
      SurroundWithUtil.indentCommentIfNecessary(thenBlock, statements);
      addRangeWithinContainer(thenBlock, container, statements, true);
      container.deleteChildRange(statements[0], statements[statements.length - 1]);
    }

    return ifStatement;
  }
}