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

import com.intellij.openapi.util.Condition;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class FormatPostfixTemplate extends StringBasedPostfixTemplate {
  private static final Condition<PsiElement> IS_STRING = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement expr) {
      if (!(expr instanceof PsiExpression)) {
        return false;
      }
      PsiType type = ((PsiExpression)expr).getType();
      return type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
    }
  };


  public FormatPostfixTemplate() {
    super("format", "String.format(expr)", selectorAllExpressionsWithCurrentOffset(IS_STRING));
  }


  @Nullable
  @Override
  public String getTemplateString(@NotNull PsiElement element) {
    return "String.format($expr$, $END$)";
  }

  @Override
  protected PsiElement getElementToRemove(PsiElement expr) {
    return expr;
  }
}
