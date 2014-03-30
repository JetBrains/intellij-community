package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class IsNullCheckPostfixTemplate extends NullCheckPostfixTemplate {
  public IsNullCheckPostfixTemplate() {
    super("null", "Checks expression to be null", "if (expr == null)");
  }

  @NotNull
  @Override
  protected String getTail() {
    return "== null";
  }
}