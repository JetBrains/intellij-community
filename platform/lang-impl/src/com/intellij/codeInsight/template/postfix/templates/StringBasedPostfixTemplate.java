// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StringBasedPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  /**
   * @deprecated use {@link #StringBasedPostfixTemplate(String, String, PostfixTemplateExpressionSelector, PostfixEditableTemplateProvider)}
   */
  public StringBasedPostfixTemplate(@NotNull String name,
                                    @NotNull String example,
                                    @NotNull PostfixTemplateExpressionSelector selector) {
    this(name, example, selector, null);
  }

  public StringBasedPostfixTemplate(@NotNull String name,
                                    @NotNull String example,
                                    @NotNull PostfixTemplateExpressionSelector selector,
                                    @Nullable PostfixEditableTemplateProvider provider) {
    super(null, name, example, selector, provider);
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


  /**
   * @deprecated use {@link StringBasedPostfixTemplate#getElementToRemove(PsiElement)} (idea 16 to remove)
   */
  protected boolean shouldRemoveParent() {
    return true;
  }

  protected PsiElement getElementToRemove(PsiElement expr) {
    if (shouldRemoveParent()) {
      return expr.getParent();
    }
    else {
      return expr;
    }
  }
}
