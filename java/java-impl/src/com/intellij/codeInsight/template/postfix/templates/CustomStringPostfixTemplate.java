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

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public class CustomStringPostfixTemplate extends StringBasedPostfixTemplate {

  private final String template;

  public CustomStringPostfixTemplate(String clazz, String name, String example, String template) {
    super(name, example, selectorTopmost(getCondition(clazz)));
    this.template = template;
  }

  @NotNull
  private static Condition<PsiElement> getCondition(String clazz) {
    if (clazz.equals("ARRAY")) {
      return IS_ARRAY;
    } if (clazz.equals("BOOLEAN")) {
      return IS_BOOLEAN;
    } if (clazz.equals("ITERABLE_OR_ARRAY")) {
      return IS_ITERABLE_OR_ARRAY;
    } if (clazz.equals("NON_VOID")) {
      return IS_NON_VOID;
    } if (clazz.equals("NOT_PRIMITIVE")) {
      return IS_NOT_PRIMITIVE;
    } if (clazz.equals("NUMBER")) {
      return IS_DECIMAL_NUMBER;
    } else {
      return isCustomClass(clazz);
    }
  }

  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return template;
  }
}
