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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithIfExpressionSurrounder extends JavaBooleanExpressionSurrounder {
  @Override
  public boolean isApplicable(PsiExpression expr) {
    if (!super.isApplicable(expr)) return false;
    if (!expr.isPhysical()) return false;
    PsiElement expressionStatement = expr.getParent();
    if (!(expressionStatement instanceof PsiExpressionStatement)) return false;

    PsiElement statementParent = expressionStatement.getParent();
    if (!isElseBranch(expr, statementParent) &&
        !(statementParent instanceof PsiCodeBlock) &&
        !(FileTypeUtils.isInServerPageFile(statementParent) && statementParent instanceof PsiFile)) {
      return false;
    }
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

    PsiExpression condition = ifStatement.getCondition();
    if (condition != null) {
      condition.replace(expr);
    }

    PsiExpressionStatement statement = (PsiExpressionStatement)expr.getParent();
    ifStatement = (PsiIfStatement)statement.replace(ifStatement);

    PsiStatement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof PsiBlockStatement) {
      PsiCodeBlock block = ((PsiBlockStatement)thenBranch).getCodeBlock();
      block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block);
      TextRange range = block.getStatements()[0].getTextRange();
      editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
      return TextRange.from(range.getStartOffset(), 0);
    }
    return TextRange.from(editor.getCaretModel().getOffset(), 0);
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.if.expression.template");
  }

  private static boolean isElseBranch(@NotNull PsiExpression expression, @NotNull PsiElement statementParent) {
    if (statementParent instanceof PsiIfStatement) {
      PsiStatement elseBranch = ((PsiIfStatement)statementParent).getElseBranch();
      if (elseBranch != null && elseBranch.getFirstChild() == expression) {
        return true;
      }
    }
    return false;
  }
}