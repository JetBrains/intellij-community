// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithDoWhileSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.dowhile.template");
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

    @NonNls String text = "do{\n}while(true);";
    PsiDoWhileStatement doWhileStatement = (PsiDoWhileStatement)factory.createStatementFromText(text, null);
    doWhileStatement = (PsiDoWhileStatement)codeStyleManager.reformat(doWhileStatement);

    doWhileStatement = (PsiDoWhileStatement)addAfter(doWhileStatement, container, statements);

    PsiStatement body = doWhileStatement.getBody();
    if (!(body instanceof PsiBlockStatement block)) return;
    PsiCodeBlock bodyBlock = block.getCodeBlock();
    SurroundWithUtil.indentCommentIfNecessary(bodyBlock, statements);
    addRangeWithinContainer(bodyBlock, container, statements, false);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiExpression condition = doWhileStatement.getCondition();
    if (condition != null) {
      updater.select(condition);
    }
  }
}