package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class AssertStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() { return "assert"; }

  public void testBooleanVariableBeforeAssignment() { doTest(); }
  public void testBoxedBooleanVariable() { doTest(); }
  public void testNotBooleanExpression() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testSeveralConditions() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testMethodInvocation() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testInstanceofBeforeReturnStatement() { doTest(); }
  public void testNotNull() { doTest(); }
}

