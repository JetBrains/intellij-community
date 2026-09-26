// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class NotNullPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "notnull";
  }

  public void testSimple() {
    doTest();
  }

  public void testPrimitive() {
    doTest();
  }

  public void testNn() {
    doTest();
  }

  public void testSecondStatement() {
    doTest();
  }

  public void testElseStatement() {
    doTest();
  }

  public static class ModNotNullPostfixTemplateTest extends NotNullPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}