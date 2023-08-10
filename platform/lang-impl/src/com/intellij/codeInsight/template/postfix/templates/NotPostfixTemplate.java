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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NotPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  @NotNull
  private final PostfixTemplatePsiInfo myPsiInfo;

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