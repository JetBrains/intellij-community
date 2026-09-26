// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class IfStatementPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "if";
  }

  public void testBooleanVariableBeforeAssignment() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
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

  public void testExtraParentheses() {
    doTest();
  }

  public void testInstanceofBeforeReturnStatement() {
    doTest();
  }

  public void testSimpleWithSemicolon() {
    doTest();
  }

  public void testBeforeAssignment() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public void testLesserOperatorExpression() {
    doTest();
  }

  public static class ModIfStatementPostfixTemplateTest extends IfStatementPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
