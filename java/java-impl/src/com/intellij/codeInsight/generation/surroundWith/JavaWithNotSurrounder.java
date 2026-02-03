
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
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.refactoring.IntroduceVariableUtil;
import org.jetbrains.annotations.NotNull;

public class JavaWithNotSurrounder extends JavaExpressionModCommandSurrounder {
  @Override
  protected void surroundExpression(@NotNull ActionContext context, @NotNull PsiExpression expr, @NotNull ModPsiUpdater updater) {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    PsiPrefixExpression prefixExpr = (PsiPrefixExpression)factory.createExpressionFromText("!(a)", null);
    prefixExpr = (PsiPrefixExpression)codeStyleManager.reformat(prefixExpr);
    ((PsiParenthesizedExpression)prefixExpr.getOperand()).getExpression().replace(expr);
    expr = (PsiExpression)IntroduceVariableUtil.replace(expr, prefixExpr, project);
    int offset = expr.getTextRange().getEndOffset();
    updater.select(TextRange.from(offset, 0));
  }

  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.not.template");
  }

  @Override
  public boolean isApplicable(PsiExpression expr) {
    PsiType type = expr.getType();
    return type != null && (PsiTypes.booleanType().equals(type) || PsiTypes.booleanType().equals(PsiPrimitiveType.getUnboxedType(type)));
  }
}