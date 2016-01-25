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
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NON_VOID;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorTopmost;

public class OptionalPostfixTemplate extends StringBasedPostfixTemplate {
  public OptionalPostfixTemplate() {
    super("opt", "Optional.ofNullable(expr)", JavaPostfixTemplatesUtils.atLeastJava8Selector(selectorTopmost(IS_NON_VOID)));
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    String defaultTemplate = "Optional.ofNullable($expr$)";
    if (!(element instanceof PsiExpression)) return defaultTemplate;

    String method = ".of($expr$)";
    PsiType type = ((PsiExpression)element).getType();
    if (PsiType.INT.equals(type)) {
      return "OptionalInt" + method;
    } else if (PsiType.DOUBLE.equals(type)) {
      return "OptionalDouble" + method;
    } else if (PsiType.LONG.equals(type)) {
      return "OptionalLong" + method;
    }
    return defaultTemplate;
  }
}
