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

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StringBasedPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  public StringBasedPostfixTemplate(@NotNull String name,
                                    @NotNull String example,
                                    @NotNull PostfixTemplateExpressionSelector selector) {
    super(name, example, selector);
  }

  @Override
  public final void expandForChooseExpression(@NotNull PsiElement expr, @NotNull Editor editor) {
    Project project = expr.getProject();
    Document document = editor.getDocument();
    PsiElement elementForRemoving = getElementToRemove(expr);
    document.deleteString(elementForRemoving.getTextRange().getStartOffset(), elementForRemoving.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    String templateString = getTemplateString(expr);
    if (templateString == null) {
      PostfixTemplatesUtils.showErrorHint(expr.getProject(), editor);
      return;
    }


    Template template = createTemplate(manager, templateString);

    if (shouldAddExpressionToContext()) {
      template.addVariable("expr", new TextExpression(expr.getText()), false);
    }

    setVariables(template, expr);
    manager.startTemplate(editor, template);
  }

  public Template createTemplate(TemplateManager manager, String templateString) {
    Template template = manager.createTemplate("", "", templateString);
    template.setToReformat(shouldReformat());
    return template;
  }

  public void setVariables(@NotNull Template template, @NotNull PsiElement element) {
  }

  @Nullable
  public abstract String getTemplateString(@NotNull PsiElement element);

  protected boolean shouldAddExpressionToContext() {
    return true;
  }

  protected boolean shouldReformat() {
    return true;
  }

  /** @deprecated use {@link StringBasedPostfixTemplate#getElementToRemove(PsiElement)} (idea 16 to remove) */
  protected boolean shouldRemoveParent() {
    return true;
  }

  protected PsiElement getElementToRemove(PsiElement expr) {
    if (shouldRemoveParent()) {
      return expr.getParent();
    } else {
      return expr;
    }
  }
}
