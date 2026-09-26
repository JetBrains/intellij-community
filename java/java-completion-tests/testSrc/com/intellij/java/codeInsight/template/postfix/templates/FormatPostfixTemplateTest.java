// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class FormatPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "format";
  }

  public void testString() {
    doTest();
  }

  public void testNotString() {
    doTest();
  }

  public void testExpression() {
    doTest();
  }

  public void testExpressionNotCompleted() {
    doTest();
  }

  public static class ModFormatPostfixTemplateTest extends FormatPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}