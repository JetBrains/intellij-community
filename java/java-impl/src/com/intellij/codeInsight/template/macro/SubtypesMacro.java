/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class SubtypesMacro extends Macro {
  @Override
  public String getName() {
    return "subtypes";
  }

  @Override
  public String getPresentableName() {
    return "subtypes(TYPE)";
  }

  @Override
  @NotNull
  public String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return null;
    return params[0].calculateQuickResult(context);
  }

  @Override
  public Result calculateQuickResult(@NotNull Expression[] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public LookupElement[] calculateLookupItems(@NotNull Expression[] params, ExpressionContext context) {
    if (params.length == 0) return LookupElement.EMPTY_ARRAY;
    Result paramResult = params[0].calculateQuickResult(context);
    if (paramResult instanceof PsiTypeResult) {
      final PsiType type = ((PsiTypeResult)paramResult).getType();
      final PsiFile file = PsiDocumentManager.getInstance(context.getProject()).getPsiFile(context.getEditor().getDocument());
      final PsiElement element = file.findElementAt(context.getStartOffset());

      final Set<LookupElement> set = new LinkedHashSet<>();
      JavaTemplateUtil.addTypeLookupItem(set, type);
      CodeInsightUtil.processSubTypes(type, element, false, PrefixMatcher.ALWAYS_TRUE,
                                      psiType -> JavaTemplateUtil.addTypeLookupItem(set, psiType));
      return set.toArray(new LookupElement[set.size()]);
    }
    return LookupElement.EMPTY_ARRAY;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}