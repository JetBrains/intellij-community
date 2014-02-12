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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public abstract class NullCheckPostfixTemplate extends PostfixTemplate {
  protected NullCheckPostfixTemplate(@NotNull String name, @NotNull String description, @NotNull String example) {
    super(name, description, example);
  }

  @NotNull
  abstract String getTail();

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return getTopmostExpression(context) != null;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null) return;

    Project project = expr.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiExpression condition = factory.createExpressionFromText(expr.getText() + getTail(), context);

    PsiElement replace = expr.replace(condition);
    assert replace instanceof PsiExpression;

    TextRange range = PostfixTemplatesUtils.ifStatement(project, editor, (PsiExpression)replace);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}
