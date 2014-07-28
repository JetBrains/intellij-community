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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


/**
 * See {@link PostfixTemplateExpressionSelector} for description
 */
public class TopmostExpressionSelector implements PostfixTemplateExpressionSelector {

  @NotNull
  private final Condition<PsiElement> myCondition;

  public TopmostExpressionSelector(@NotNull Condition<PsiElement> condition) {

    myCondition = condition;
  }

  @Override
  public boolean hasExpression(@NotNull PostfixTemplateWithExpressionSelector template,
                               @NotNull PsiElement context,
                               @NotNull Document copyDocument,
                               int newOffset) {
    PsiElement topmostExpression = template.getPsiInfo().getTopmostExpression(context);
    return topmostExpression != null && myCondition.value(topmostExpression);
  }

  @Override
  public void expandTemplate(@NotNull PostfixTemplateWithExpressionSelector template,
                             @NotNull PsiElement context,
                             @NotNull Editor editor) {
    PostfixTemplatePsiInfo info = template.getPsiInfo();
    PsiElement expression = info.getTopmostExpression(context);
    if (expression == null) {
      return;
    }
    template.expandForChooseExpression(expression, editor);
  }
}
