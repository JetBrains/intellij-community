// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ThrowStatementPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "throw";
  }

  public void testSimple() {
    doTest();
  }

  public void testNotThrowable() {
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

  public static class ModThrowStatementPostfixTemplateTest extends ThrowStatementPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
