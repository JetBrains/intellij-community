// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.surroundWith.PsiUpdateModCommandSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

final class SurroundModExpandAction implements ExpressionSelectorModExpander.ModExpandAction {
  private final @NotNull SurroundPostfixTemplateBase myTemplate;

  SurroundModExpandAction(@NotNull SurroundPostfixTemplateBase template) {
    myTemplate = template;
  }

  @Override
  public void expand(@NotNull ActionContext ctx, @NotNull ModPsiUpdater updater, @NotNull PsiElement elementInCopy) {
    Surrounder surrounder = myTemplate.getSurrounder();
    if (!(surrounder instanceof PsiUpdateModCommandSurrounder modCommandSurrounder)) {
      return;
    }
    PsiElement expression = myTemplate.getReplacedExpression(elementInCopy);
    if (!modCommandSurrounder.isApplicable(new PsiElement[]{expression})) {
      return;
    }
    modCommandSurrounder.surroundElements(ctx, new PsiElement[]{expression}, updater);
  }
}