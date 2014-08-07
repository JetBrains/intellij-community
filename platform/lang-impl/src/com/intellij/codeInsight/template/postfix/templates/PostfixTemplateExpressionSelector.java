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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Interface provides method used in {@link com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector}
 *
 * You should implement the interface if you have non-trivial logic how to determine expression for next processing in postfix template
 * Otherwise, you can use one of existing simple implementations:
 *
 * 1) {@link com.intellij.codeInsight.template.postfix.templates.ChooserExpressionSelector} - The selector get all expression
 * in the current position and show to user chooser for these expressions.
 *
 * 2) {@link com.intellij.codeInsight.template.postfix.templates.TopmostExpressionSelector} - The selector pass to postfix template
 * top most expression in the current position
 *
 *
 */
public interface PostfixTemplateExpressionSelector {

  /**
   * Check that we can select not-null expression(PsiElement) in current context
   */
  boolean hasExpression(@NotNull final PostfixTemplateWithExpressionSelector postfixTemplate,
                        @NotNull PsiElement context,
                        @NotNull Document copyDocument,
                        int newOffset);

  /**
   * Select expression(PsiElement)  and call postfixTemplate.expandForChooseExpression for selected expression
   */
  void expandTemplate(@NotNull final PostfixTemplateWithExpressionSelector postfixTemplate,
                      @NotNull PsiElement context,
                      @NotNull final Editor editor);
}
