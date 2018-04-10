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

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class StreamPostfixTemplate extends StringBasedPostfixTemplate {
  private static final Condition<PsiElement> IS_SUPPORTED_ARRAY = element -> {
    if (!(element instanceof PsiExpression)) return false;

    PsiType type = ((PsiExpression)element).getType();
    if (!(type instanceof PsiArrayType)) return false;

    PsiType componentType = ((PsiArrayType)type).getComponentType();
    if (!(componentType instanceof PsiPrimitiveType)) return true;

    return componentType.equals(PsiType.INT) || componentType.equals(PsiType.LONG) || componentType.equals(PsiType.DOUBLE);
  };

  public StreamPostfixTemplate() {
    super("stream", "Arrays.stream(expr)", JavaPostfixTemplatesUtils.atLeastJava8Selector(selectorAllExpressionsWithCurrentOffset(IS_SUPPORTED_ARRAY)));
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return "java.util.Arrays.stream($expr$)";
  }

  @Override
  protected PsiElement getElementToRemove(PsiElement expr) {
    return expr;
  }
}
