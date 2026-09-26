// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class AssertStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "assert";
  }

  public void testBooleanVariableBeforeAssignment() {
    doTest();
  }

  public void testBoxedBooleanVariable() {
    doTest();
  }

  public void testNotBooleanExpression() {
    doTest();
  }

  public void testUnresolvedVariable() {
    doTest();
  }

  public void testSeveralConditions() {
    doTest();
  }

  public void testIntegerComparison() {
    doTest();
  }

  public void testMethodInvocation() {
    doTest();
  }

  public void testInstanceof() {
    doTest();
  }

  public void testInstanceofBeforeReturnStatement() {
    doTest();
  }

  public void testNotNull() {
    doTest();
  }

  public void testBeforeAssignment() {
    doTest();
  }

  public void testSimpleWithSemicolon() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public static class ModAssertStatementPostfixTemplateTest extends AssertStatementPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}

