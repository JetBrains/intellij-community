
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

class JavaWithNotInstanceofSurrounder extends JavaExpressionSurrounder{
  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (type == null) return false;
    if (!expr.isPhysical()) return false;
    return !(type instanceof PsiPrimitiveType);
  }

  @Override
  public TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiManager manager = expr.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a instanceof Type)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    PsiParenthesizedExpression parenthExpr = (PsiParenthesizedExpression)prefixExpr.getOperand();
    PsiInstanceOfExpression instanceofExpr = (PsiInstanceOfExpression)parenthExpr.getExpression();
    instanceofExpr.getOperand().replace(expr);
    prefixExpr = (PsiPrefixExpression)expr.replace(prefixExpr);
    parenthExpr = (PsiParenthesizedExpression)prefixExpr.getOperand();
    instanceofExpr = (PsiInstanceOfExpression)parenthExpr.getExpression();
    instanceofExpr = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(instanceofExpr);
    TextRange range = instanceofExpr.getCheckType().getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    return new TextRange(range.getStartOffset(), range.getStartOffset());
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.not.instanceof.template");
  }
}