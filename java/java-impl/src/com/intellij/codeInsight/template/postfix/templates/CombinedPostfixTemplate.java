/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.List;
import java.util.Optional;

public class CombinedPostfixTemplate extends PostfixTemplate {

  private List<PostfixTemplate> myTemplates;
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private Optional<PostfixTemplate> myApplicableTemplate;

  public CombinedPostfixTemplate(@NotNull String name,
                                 @NotNull String example,
                                 List<PostfixTemplate> templates) {
    super(name, name, example);
    this.myTemplates = templates;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    myApplicableTemplate = myTemplates.stream().filter(t -> t.isApplicable(context, copyDocument, newOffset)).findFirst();

    return myApplicableTemplate.isPresent();
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    myApplicableTemplate.ifPresent(t -> t.expand(context, editor));
  }
}
