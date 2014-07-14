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

public class NotPostfixTemplate extends ExpressionPostfixTemplateWithChooser {

  public NotPostfixTemplate(@NotNull PostfixTemplatePsiInfoBase info) {
    super("not", "!expr", info);
  }


  public NotPostfixTemplate(@NotNull String name,
                            @NotNull String key,
                            @NotNull String example,
                            @NotNull PostfixTemplatePsiInfoBase info) {
    super(name, key, example, info);
  }

  @Override
  protected void doIt(@NotNull Editor editor, @NotNull PsiElement expression) {
    PsiElement element = myInfo.getNegatedExpression(expression);
    expression.replace(element);
  }
}