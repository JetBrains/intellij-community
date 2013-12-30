package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
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
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    return parent instanceof PsiExpressionStatement;
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null) return;

    Project project = expr.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiBinaryExpression condition = (PsiBinaryExpression)factory.createExpressionFromText(expr.getText() + getTail(), context);

    PsiElement replace = expr.replace(condition);
    assert replace instanceof PsiExpression;

    TextRange range = PostfixTemplatesUtils.ifStatement(project, editor, (PsiExpression)replace);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}
