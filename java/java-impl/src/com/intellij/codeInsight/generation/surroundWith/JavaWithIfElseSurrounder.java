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

public class JavaWithIfElseSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.ifelse.template");
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

    @NonNls String text = "if(a){\n}else{\n}";
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(text, null);
    ifStatement = (PsiIfStatement)codeStyleManager.reformat(ifStatement);

    ifStatement = (PsiIfStatement)addAfter(ifStatement, container, statements);

    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (!(thenBranch instanceof PsiBlockStatement)) return;
    PsiCodeBlock thenBlock = ((PsiBlockStatement)thenBranch).getCodeBlock();
    SurroundWithUtil.indentCommentIfNecessary(thenBlock, statements);
    addRangeWithinContainer(thenBlock, container, statements, true);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);
    ifStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(ifStatement);
    if (ifStatement == null) return;
    PsiExpression condition = ifStatement.getCondition();
    if (condition == null) return;
    TextRange range = condition.getTextRange();
    condition.getContainingFile().getFileDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    updater.select(TextRange.from(range.getStartOffset(), 0));
  }
}