// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class TryPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "try";
  }

  public void testSimple() {
    doTest();
  }

  public void testMultiStatement() {
    doTest();
  }

  public void testNotStatement() {
    doTest();
  }

  public void testNotResolvedExpression() {
    doTest();
  }

  public void testDeclarationStatement() {
    doTest();
  }

  public void testExpressionInMethodBody() {
    doTest();
  }

  public void testSimpleWithThrowsCheckedException() {
    doTest();
  }

  public void testIncompleteStatement() {
    doTest();
  }

  public void testConstructorStatement() {
    doTest();
  }

  public void testAfterLambda() {
    doTest();
  }

  public static class ModTryPostfixTemplateTest extends TryPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
