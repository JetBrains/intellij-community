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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.TemplateSubstitutor;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaTemplateSubstitutor implements TemplateSubstitutor {
  private static final ElementPattern<PsiElement> EXPR_LAMBDA_BODY = psiElement().afterLeaf(psiElement(JavaTokenType.ARROW));

  @Nullable
  @Override
  public TemplateImpl substituteTemplate(@NotNull PsiFile file, int caretOffset, @NotNull TemplateImpl template) {
    if (file.getLanguage().isKindOf(JavaLanguage.INSTANCE) && EXPR_LAMBDA_BODY.accepts(file.findElementAt(caretOffset))) {
      String text = template.getString();
      if (!text.contains("\n") && text.endsWith(";")) {
        TemplateImpl copy = template.copy();
        copy.setString(StringUtil.trimEnd(text, ";"));
        return copy;
      }
    }
    return null;
  }
}
