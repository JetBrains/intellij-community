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

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class StringBasedPostfixTemplate extends TypedPostfixTemplate {

  public StringBasedPostfixTemplate(@NotNull String name,
                                    @NotNull String example,
                                    @NotNull PostfixTemplatePsiInfo psiInfo,
                                    @NotNull Condition<PsiElement> typeChecker) {
    super(name, example, psiInfo, typeChecker);
  }

  @Override
  public final void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiElement expr = myPsiInfo.getTopmostExpression(context);
    assert expr != null;
    Project project = context.getProject();
    Document document = editor.getDocument();
    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);
    expandWithTemplateManager(manager, expr, editor);
  }

  public abstract void expandWithTemplateManager(TemplateManager manager, PsiElement expression, Editor editor);
}
