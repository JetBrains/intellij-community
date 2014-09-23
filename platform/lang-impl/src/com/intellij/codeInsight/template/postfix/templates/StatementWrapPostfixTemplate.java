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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


public abstract class StatementWrapPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  @SuppressWarnings("unchecked")
  protected StatementWrapPostfixTemplate(@NotNull String name,
                                         @NotNull String descr,
                                         @NotNull PostfixTemplatePsiInfo psiInfo) {
    super(name, descr, psiInfo, Conditions.<PsiElement>alwaysTrue());
  }

  protected StatementWrapPostfixTemplate(@NotNull String name,
                                         @NotNull String descr,
                                         @NotNull PostfixTemplatePsiInfo psiInfo,
                                         @NotNull Condition<PsiElement> typeChecker) {
    super(name, descr, psiInfo, typeChecker);
  }

  @Override
  public void expandForChooseExpression(@NotNull PsiElement topmostExpression, @NotNull Editor editor) {
    PsiElement parent = topmostExpression.getParent();
    PsiElement expression = getWrappedExpression(topmostExpression);
    PsiElement replace = parent.replace(expression);
    afterExpand(replace, editor);
  }

  protected PsiElement getWrappedExpression(PsiElement expression) {
    if (StringUtil.isEmpty(getHead()) && StringUtil.isEmpty(getTail())) {
      return expression;
    }
    return createNew(expression);
  }

  protected PsiElement createNew(PsiElement expression) {
    if (isStatement()) {
      return myPsiInfo.createStatement(expression, getHead(), getTail());
    }
    return myPsiInfo.createExpression(expression, getHead(), getTail());
  }

  protected void afterExpand(@NotNull PsiElement newElement, @NotNull Editor editor) {
    editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset());
  }

  @NotNull
  protected String getHead() {
    return "";
  }

  @NotNull
  protected String getTail() {
    return "";
  }

  public boolean isStatement() {
    return true;
  }
}
