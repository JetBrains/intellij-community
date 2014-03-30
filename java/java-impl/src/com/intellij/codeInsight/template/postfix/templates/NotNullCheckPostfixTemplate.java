package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.Aliases;
import org.jetbrains.annotations.NotNull;

@Aliases(".nn")
public class NotNullCheckPostfixTemplate extends NullCheckPostfixTemplate {
  public NotNullCheckPostfixTemplate() {
    super("notnull", "Checks expression to be not-null", "if (expr != null)");
  }

  @NotNull
  @Override
  protected String getTail() {
    return "!= null";
  }
}