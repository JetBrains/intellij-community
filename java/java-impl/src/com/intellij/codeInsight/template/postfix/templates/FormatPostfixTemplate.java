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

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class FormatPostfixTemplate extends PostfixTemplate {
  public FormatPostfixTemplate() {
    super("format", "Creates String.format call", "String.format(expr);");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PostfixTemplatesUtils.createStatement(context, editor, "String.format(", ", )", -2);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = PostfixTemplatesUtils.getTopmostExpression(context);
    PsiType type = expr != null ? expr.getType() : null;
    return expr != null && type != null && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText());
  }
}
