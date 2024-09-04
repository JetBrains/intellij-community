// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector implements DumbAware {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    var supportProvider = LanguageRefactoringSupport.getInstance().forLanguage(JavaLanguage.INSTANCE);
    JavaIntroduceFieldHandlerBase handler = (JavaIntroduceFieldHandlerBase)supportProvider.getIntroduceFieldHandler();
    assert handler != null;
    handler.invoke(expression.getProject(), expression, editor);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    DumbService.getInstance(expression.getProject())
      .withAlternativeResolveEnabled(() -> expandForChooseExpression(expression, editor));
  }
}