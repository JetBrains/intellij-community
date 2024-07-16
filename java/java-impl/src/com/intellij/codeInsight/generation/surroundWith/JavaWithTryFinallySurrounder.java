/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiNavigator;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithTryFinallySurrounder extends JavaStatementsModCommandSurrounder {
  @SuppressWarnings("DialogTitleCapitalization")
  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.try.finally.template");
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) throws IncorrectOperationException {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0) return;

    @NonNls String text = "try{\n}finally{\n\n}";
    PsiTryStatement tryStatement = (PsiTryStatement)factory.createStatementFromText(text, null);
    tryStatement = (PsiTryStatement)codeStyleManager.reformat(tryStatement);

    tryStatement = (PsiTryStatement)addAfter(tryStatement, container, statements);

    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock == null) return;
    SurroundWithUtil.indentCommentIfNecessary(tryBlock, statements);
    addRangeWithinContainer(tryBlock, container, statements, true);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock == null) return;
    moveCaretToFinallyBlock(project, updater, finallyBlock);
  }

  public static void moveCaretToFinallyBlock(Project project, ModPsiNavigator navigator, PsiCodeBlock finallyBlock) {
    Document document = finallyBlock.getContainingFile().getFileDocument();
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
    TextRange finallyBlockRange = finallyBlock.getTextRange();
    int newLineOffset = finallyBlockRange.getStartOffset() + 2;
    navigator.moveCaretTo(CodeStyleManager.getInstance(project).adjustLineIndent(document, newLineOffset));
    PsiDocumentManager.getInstance(project).commitDocument(document);
  }
}