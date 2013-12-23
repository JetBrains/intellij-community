package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class SynchronizedStatementPostfixTemplate extends StatementPostfixTemplateBase {
  public SynchronizedStatementPostfixTemplate() {
    super("synchronized", "Produces synchronization statement", "synchronized (expr)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expression = getTopmostExpression(context);
    PsiElement parent = expression != null ? expression.getParent() : null;
    PsiType type = expression != null ? expression.getType() : null;
    return parent instanceof PsiExpressionStatement && type != null && !(type instanceof PsiPrimitiveType);
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    surroundWith(context, editor, "synchronized");
  }
}