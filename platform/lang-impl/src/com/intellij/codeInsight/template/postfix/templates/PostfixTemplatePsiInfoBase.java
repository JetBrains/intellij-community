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
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class PostfixTemplatePsiInfoBase implements PostfixTemplatePsiInfo {

  @NotNull
  public abstract List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int newOffset);

  @NotNull
  public Function<PsiElement, String> getRenderer() {
    return new Function<PsiElement, String>() {
      @Override
      public String fun(@NotNull PsiElement element) {
        return element.getText();
      }
    };
  }
}
