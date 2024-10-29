// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private final @NotNull PostfixTemplatePsiInfo myPsiInfo;

  /**
   * @deprecated use {@link #NotPostfixTemplate(PostfixTemplatePsiInfo, PostfixTemplateExpressionSelector, PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  public NotPostfixTemplate(@NotNull PostfixTemplatePsiInfo info,
                            @NotNull PostfixTemplateExpressionSelector selector) {
    this(info, selector, null);
  }

  public NotPostfixTemplate(@NotNull PostfixTemplatePsiInfo info,
                            @NotNull PostfixTemplateExpressionSelector selector,
                            @Nullable PostfixTemplateProvider provider) {
    super(null, "not", "!expr", selector, provider);
    myPsiInfo = info;
  }

  /**
   * @deprecated use {@link #NotPostfixTemplate(String,String,String,PostfixTemplatePsiInfo,PostfixTemplateExpressionSelector,PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  public NotPostfixTemplate(@NotNull String name,
                            @NotNull String key,
                            @NotNull String example,
                            @NotNull PostfixTemplatePsiInfo info,
                            @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, key, example, selector);
    myPsiInfo = info;
  }

  public NotPostfixTemplate(@Nullable String id,
                            @NotNull String name,
                            @NotNull String example,
                            @NotNull PostfixTemplatePsiInfo info,
                            @NotNull PostfixTemplateExpressionSelector selector,
                            @Nullable PostfixTemplateProvider provider) {
    super(id, name, example, selector, provider);
    myPsiInfo = info;
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    PsiElement element = myPsiInfo.getNegatedExpression(expression);
    expression.replace(element);
  }
}