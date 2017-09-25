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
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.NullnessUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
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
    String className = "Optional";

    PsiType type = ((PsiExpression)element).getType();
    if (type instanceof PsiPrimitiveType) {
      if (PsiType.INT.equals(type)) {
        className = "OptionalInt";
      }
      else if (PsiType.DOUBLE.equals(type)) {
        className = "OptionalDouble";
      }
      else if (PsiType.LONG.equals(type)) {
        className = "OptionalLong";
      }
    }

    String methodName = Nullness.NOT_NULL.equals(NullnessUtil.getExpressionNullness((PsiExpression)element)) ? "of" : "ofNullable";
    return "java.util." + className + "." + methodName + "($expr$)";
  }
}
