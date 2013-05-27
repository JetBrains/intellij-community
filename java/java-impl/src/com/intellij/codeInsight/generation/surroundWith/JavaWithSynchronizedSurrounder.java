
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
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

class JavaWithSynchronizedSurrounder extends JavaStatementsSurrounder{
  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.synchronized.template");
  }

  @Override
  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = PsiManager.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    statements = SurroundWithUtil.moveDeclarationsOut(container, statements, false);
    if (statements.length == 0){
      return null;
    }

    @NonNls String text = "synchronized(a){\n}";
    PsiSynchronizedStatement synchronizedStatement = (PsiSynchronizedStatement)factory.createStatementFromText(text, null);
    synchronizedStatement = (PsiSynchronizedStatement)codeStyleManager.reformat(synchronizedStatement);

    synchronizedStatement = (PsiSynchronizedStatement)container.addAfter(synchronizedStatement, statements[statements.length - 1]);

    PsiCodeBlock synchronizedBlock = synchronizedStatement.getBody();
    if (synchronizedBlock == null) {
      return null;
    }
    SurroundWithUtil.indentCommentIfNecessary(synchronizedBlock, statements);
    synchronizedBlock.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    synchronizedStatement = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(synchronizedStatement);
    PsiExpression lockExpression = synchronizedStatement.getLockExpression();
    if (lockExpression == null) {
      return null;
    }
    TextRange range = lockExpression.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }
}