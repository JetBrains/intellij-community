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
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;
    TextRange range = PostfixTemplatesUtils.ifStatement(expression.getProject(), editor, expression);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}

