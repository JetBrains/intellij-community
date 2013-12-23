package com.intellij.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class IfStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariableBeforeAssignment() { doTest(); }
  public void _testBoxedBooleanVariable() { doTest(); } //todo: platform changes if required
  public void testNotBooleanExpression() { doTest(); }
  public void testUnresolvedVariable() { doTest(); }
  public void testSeveralConditions() { doTest(); }
  public void testIntegerComparison() { doTest(); }
  public void testMethodInvocation() { doTest(); }
  public void testInstanceof() { doTest(); }
  public void testInstanceofBeforeReturnStatement() { doTest(); }

  @NotNull
  @Override
  protected String getSuffix() { return "if"; }
}
