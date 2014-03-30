package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class AssertStatementPostfixTemplate extends BooleanPostfixTemplate {
  public AssertStatementPostfixTemplate() {
    super("assert", "Creates assertion from boolean expression", "assert expr;");
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PostfixTemplatesUtils.createSimpleStatement(context, editor, "assert");
  }
}