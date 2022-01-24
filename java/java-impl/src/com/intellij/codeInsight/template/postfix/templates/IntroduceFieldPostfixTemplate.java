/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldHandlerBase;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class IntroduceFieldPostfixTemplate extends PostfixTemplateWithExpressionSelector {
  public IntroduceFieldPostfixTemplate() {
    super("field", "myField = expr", selectorAllExpressionsWithCurrentOffset(IS_NON_VOID));
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    JavaIntroduceFieldHandlerBase handler;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      handler = getMockHandler(expression);
    }
    else {
      var supportProvider = LanguageRefactoringSupport.INSTANCE.forLanguage(JavaLanguage.INSTANCE);
      handler = (JavaIntroduceFieldHandlerBase)supportProvider.getIntroduceFieldHandler();
    }
    assert handler != null;
    handler.invoke(expression.getProject(), expression, editor);
  }

  @NotNull
  private static JavaIntroduceFieldHandlerBase getMockHandler(@NotNull final PsiElement expression) {
    return JavaSpecialRefactoringProvider.getInstance().getMockIntroduceFieldHandler(expression);
  }

  @Override
  protected void prepareAndExpandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    //no write action
    expandForChooseExpression(expression, editor);
  }
}