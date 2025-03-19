// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("PostfixTemplateDescriptionNotFound")
public class ParenthesizedPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private final PostfixTemplatePsiInfo myPsiInfo;

  /**
   * @deprecated use {@link #ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo, PostfixTemplateExpressionSelector, PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  public ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo psiInfo,
                                      @NotNull PostfixTemplateExpressionSelector selector) {
    this(psiInfo, selector, null);
  }

  public ParenthesizedPostfixTemplate(PostfixTemplatePsiInfo psiInfo,
                                      @NotNull PostfixTemplateExpressionSelector selector,
                                      @Nullable PostfixTemplateProvider provider) {
    super(null, "par", "(expr)", selector, provider);
    myPsiInfo = psiInfo;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    expression.replace(myPsiInfo.createExpression(expression, "(", ")"));
  }
}