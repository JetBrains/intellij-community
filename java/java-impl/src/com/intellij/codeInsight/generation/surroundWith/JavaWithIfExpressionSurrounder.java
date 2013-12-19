
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
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NonNls;

public class JavaWithIfExpressionSurrounder extends JavaExpressionSurrounder{
  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (PsiType.BOOLEAN != type) return false;
    if (!expr.isPhysical()) return false;
    PsiElement parent = expr.getParent();
    if (!(parent instanceof PsiExpressionStatement)) return false;
    final PsiElement element = parent.getParent();
    if (!(element instanceof PsiCodeBlock) && !(FileTypeUtils.isInServerPageFile(element)  && element instanceof PsiFile)) return false;
    return true;
  }

  @Override
  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    @NonNls String text = "if(a){\nst;\n}";
    PsiIfStatement ifStatement = (PsiIfStatement)factory.createStatementFromText(text, null);
    ifStatement = (PsiIfStatement)codeStyleManager.reformat(ifStatement);

    ifStatement.getCondition().replace(expr);

    PsiExpressionStatement statement = (PsiExpressionStatement)expr.getParent();
    ifStatement = (PsiIfStatement)statement.replace(ifStatement);

    PsiCodeBlock block = ((PsiBlockStatement)ifStatement.getThenBranch()).getCodeBlock();
    block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block);
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.if.expression.template");
  }
}