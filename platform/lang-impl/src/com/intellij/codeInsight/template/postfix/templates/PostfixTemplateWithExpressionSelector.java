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

import static com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils.selectorTopmost;

public abstract class PostfixTemplateWithExpressionSelector extends PostfixTemplate {

  @NotNull
  protected final PostfixTemplatePsiInfo myPsiInfo;
  @NotNull
  private final PostfixTemplateExpressionSelector mySelector;

  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String key,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplatePsiInfo psiInfo,
                                                  @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, key, example);
    myPsiInfo = psiInfo;
    mySelector = selector;
  }


  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplatePsiInfo psiInfo,
                                                  @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, example);
    myPsiInfo = psiInfo;
    mySelector = selector;
  }

  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplatePsiInfo psiInfo,
                                                  @NotNull Condition<PsiElement> typeChecker) {
    this(name, example, psiInfo, selectorTopmost(typeChecker));
  }

  protected PostfixTemplateWithExpressionSelector(@NotNull String name,
                                                  @NotNull String example,
                                                  @NotNull PostfixTemplatePsiInfo psiInfo) {
    this(name, example, psiInfo, selectorTopmost());
  }


  @Override
  public final boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return mySelector.hasExpression(this, context, copyDocument, newOffset);
  }

  @Override
  public final void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    mySelector.expandTemplate(this, context, editor);
  }

  protected abstract void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor);

  @NotNull
  PostfixTemplatePsiInfo getPsiInfo() {
    return myPsiInfo;
  }
}
