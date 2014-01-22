package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class WhileStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable()       { doTest(); }
  public void testBoxedBooleanVariable()  { doTest(); }
  public void testStringVariable()        { doTest(); }
  public void testUnresolvedVariable()    { doTest(); }

  @NotNull
  @Override
  protected String getSuffix() { return "while"; }
}
