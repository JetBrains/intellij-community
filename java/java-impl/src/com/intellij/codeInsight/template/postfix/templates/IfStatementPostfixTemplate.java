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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class IfStatementPostfixTemplate extends BooleanPostfixTemplate {
  public IfStatementPostfixTemplate() {
    super("if", "Checks boolean expression to be 'true'", "if (expr)");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull final Editor editor) {
    PsiExpression expression = PostfixTemplatesUtils.getTopmostExpression(context);
    assert expression != null;
    TextRange range = PostfixTemplatesUtils.ifStatement(expression.getProject(), editor, expression);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}

