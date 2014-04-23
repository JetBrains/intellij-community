/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ignatov
 */
public abstract class StatementPostfixTemplateBase extends PostfixTemplate {
  protected StatementPostfixTemplateBase(String name, String description, String example) {
    super(name, description, example);
  }

  protected void surroundWith(PsiElement context, Editor editor, String text) {
    PsiExpression expr = PostfixTemplatesUtils.getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    if (!(parent instanceof PsiExpressionStatement)) return;

    Project project = context.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    PsiElement statement = codeStyleManager.reformat(factory.createStatementFromText(text + " (" + expr.getText() + ") {\nst;\n}", context));
    statement = parent.replace(statement);

    //noinspection ConstantConditions
    PsiCodeBlock block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(PsiTreeUtil.getChildOfType(statement, PsiCodeBlock.class));
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    editor.getCaretModel().moveToOffset(range.getStartOffset());
  }
}
