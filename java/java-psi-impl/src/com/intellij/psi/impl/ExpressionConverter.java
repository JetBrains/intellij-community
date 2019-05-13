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
package com.intellij.psi.impl;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public abstract class ExpressionConverter {
  public static final LanguageExtension<ExpressionConverter> EP =
    new LanguageExtension<>("com.intellij.expressionConverter");

  protected abstract PsiElement convert(PsiElement expression, Project project);

  @Nullable
  public static PsiElement getExpression(PsiElement expression, Language language, Project project) {
    if (expression.getLanguage() == language) return expression;

    final ExpressionConverter converter = EP.forLanguage(language);
    if (converter == null) return null;
    return converter.convert(expression, project);
  }
}
