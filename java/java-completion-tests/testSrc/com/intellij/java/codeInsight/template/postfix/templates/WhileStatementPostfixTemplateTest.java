// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class WhileStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testBooleanVariable() {
    doTest();
  }

  public void testBoxedBooleanVariable() {
    doTest();
  }

  public void testStringVariable() {
    doTest();
  }

  public void testUnresolvedVariable() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "while";
  }

  public static class ModWhileStatementPostfixTemplateTest extends WhileStatementPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
