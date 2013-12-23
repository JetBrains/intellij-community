package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ThrowStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "throw"; }

  public void testSimple() { doTest(); }
}
