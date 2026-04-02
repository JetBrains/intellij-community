// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
final public class StringBasedModExpandAction implements ExpressionSelectorModExpander.ModExpandAction {
  private final @NotNull StringBasedPostfixTemplate myTemplate;

  public StringBasedModExpandAction(@NotNull StringBasedPostfixTemplate template) {
    myTemplate = template;
  }

  @Override
  public void expand(@NotNull ActionContext ctx, @NotNull ModPsiUpdater updater, @NotNull PsiElement elementInCopy) {
    String templateString = myTemplate.getTemplateString(elementInCopy);
    if (templateString == null) {
      return;
    }
    String exprText = elementInCopy.getText();
    PsiElement writableExpr = updater.getWritable(elementInCopy);
    PsiElement elementForRemoving = myTemplate.getElementToRemove(writableExpr);

    TemplateImpl template = (TemplateImpl)myTemplate.createTemplate(TemplateManager.getInstance(ctx.project()), templateString);
    template.addVariable(StringBasedPostfixTemplate.EXPR, new TextExpression(exprText), false);
    myTemplate.setVariables(template, writableExpr);

    TextRange range = elementForRemoving.getTextRange();
    updater.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManagerImpl.updateTemplate(template, updater);
  }
}