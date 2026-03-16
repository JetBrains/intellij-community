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
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaWithIfExpressionSurrounder extends JavaExpressionModCommandSurrounder {
  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (!(type != null && (PsiTypes.booleanType().equals(type) || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(type))))) return false;
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
  protected void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
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
    if (thenBranch instanceof PsiBlockStatement blockStatement) {
      PsiCodeBlock block = blockStatement.getCodeBlock();
      block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(block);
      TextRange range = block.getStatements()[0].getTextRange();
      block.getContainingFile().getFileDocument().deleteString(range.getStartOffset(), range.getEndOffset());
      updater.select(TextRange.from(range.getStartOffset(), 0));
    }
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.if.expression.template");
  }

  private static boolean isElseBranch(@NotNull PsiExpression expression, @NotNull PsiElement statementParent) {
    if (statementParent instanceof PsiIfStatement ifStatement) {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch != null && elseBranch.getFirstChild() == expression) {
        return true;
      }
    }
    return false;
  }
}