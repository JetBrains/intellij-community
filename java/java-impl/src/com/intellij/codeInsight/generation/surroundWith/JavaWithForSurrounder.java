
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

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithForSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.for.template");
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) throws IncorrectOperationException {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, true);
    if (statements.length == 0) return;

    @NonNls String text = "for(a;b;c){\n}";
    PsiForStatement forStatement = (PsiForStatement)factory.createStatementFromText(text, null);
    forStatement = (PsiForStatement)codeStyleManager.reformat(forStatement);

    forStatement = (PsiForStatement)addAfter(forStatement, container, statements);

    PsiStatement body = forStatement.getBody();
    if (!(body instanceof PsiBlockStatement block)) return;
    PsiCodeBlock bodyBlock = block.getCodeBlock();
    SurroundWithUtil.indentCommentIfNecessary(bodyBlock, statements);
    addRangeWithinContainer(bodyBlock, container, statements, false);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    forStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(forStatement);
    PsiStatement initialization = forStatement.getInitialization();
    if (initialization == null) return;
    TextRange range1 = initialization.getTextRange();

    PsiStatement update = forStatement.getUpdate();
    if (update == null) return;
    TextRange range3 = update.getTextRange();
    update.getContainingFile().getFileDocument().deleteString(range1.getStartOffset(), range3.getEndOffset());
    updater.select(TextRange.from(range1.getStartOffset(), 0));
  }
}