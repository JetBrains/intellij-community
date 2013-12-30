package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ElseStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "else"; }

  public void testBooleanVariable() { doTest(); }
  public void testBoxedBooleanVariable() { doTest(); }
  public void testBitOperations() { doTest(); }
  public void testBitOperationsWithMethod() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testLogicalOperations() { doTest(); }

  public void testNotNull() { doTest(); }
}
