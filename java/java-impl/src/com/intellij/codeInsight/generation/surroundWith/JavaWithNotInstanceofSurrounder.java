
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
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class JavaWithNotInstanceofSurrounder extends JavaExpressionModCommandSurrounder {
  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    if (type == null) return false;
    if (!expr.isPhysical()) return false;
    return !(type instanceof PsiPrimitiveType);
  }

  @Override
  protected void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a instanceof Type)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    PsiParenthesizedExpression parenthExpr = (PsiParenthesizedExpression)Objects.requireNonNull(prefixExpr.getOperand());
    PsiInstanceOfExpression instanceofExpr = (PsiInstanceOfExpression)Objects.requireNonNull(parenthExpr.getExpression());
    instanceofExpr.getOperand().replace(expr);
    prefixExpr = (PsiPrefixExpression)expr.replace(prefixExpr);
    parenthExpr = (PsiParenthesizedExpression)Objects.requireNonNull(prefixExpr.getOperand());
    instanceofExpr = (PsiInstanceOfExpression)Objects.requireNonNull(parenthExpr.getExpression());
    instanceofExpr = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(instanceofExpr);
    TextRange range = instanceofExpr.getCheckType().getTextRange();
    instanceofExpr.getContainingFile().getFileDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    updater.select(TextRange.from(range.getStartOffset(), 0));
  }

  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.not.instanceof.template");
  }
}