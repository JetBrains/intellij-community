package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class WhileStatementPostfixTemplate extends BooleanPostfixTemplate {
  public WhileStatementPostfixTemplate() {
    super("while", "Iterating while boolean statement is 'true'", "while (expr)");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expression = getTopmostExpression(context);
    assert expression != null;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    PsiWhileStatement whileStatement = (PsiWhileStatement)factory.createStatementFromText("while(expr)", context);
    PsiExpression condition = whileStatement.getCondition();
    assert condition != null;
    condition.replace(expression);
    PsiElement replacedWhileStatement = expression.replace(whileStatement);
    if (replacedWhileStatement instanceof PsiWhileStatement) {
      PsiJavaToken parenth = ((PsiWhileStatement)replacedWhileStatement).getRParenth();
      if (parenth != null) {
        editor.getCaretModel().moveToOffset(parenth.getTextRange().getEndOffset());
      }
    }
  }
}

