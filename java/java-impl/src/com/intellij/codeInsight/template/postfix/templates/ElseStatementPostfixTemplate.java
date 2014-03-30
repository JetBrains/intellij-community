package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class ElseStatementPostfixTemplate extends BooleanPostfixTemplate {
  public ElseStatementPostfixTemplate() {
    super("else", "Checks boolean expression to be 'false'", "if (!expr)");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;
    PsiExpression invertedExpression = (PsiExpression)expression.replace(CodeInsightServicesUtil.invertCondition(expression));
    TextRange range = PostfixTemplatesUtils.ifStatement(invertedExpression.getProject(), editor, invertedExpression);
    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }
}
