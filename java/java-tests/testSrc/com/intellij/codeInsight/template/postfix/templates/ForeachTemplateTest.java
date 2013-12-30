package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ForeachTemplateTest extends PostfixTemplateTestCase {
  public void testInts() { doTest(); }

  @NotNull
  @Override
  protected String getSuffix() { return "for"; }
}
