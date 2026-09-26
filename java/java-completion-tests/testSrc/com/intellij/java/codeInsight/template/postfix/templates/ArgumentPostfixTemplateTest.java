// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ArgumentPostfixTemplateTest extends PostfixTemplateTestCase {
  public void testSimple() {
    doTest();
  }
  
  public void testLocalVariable() {
    doTest();
  }

  public void testInStatement() {
    doTest();
  }

  @Override
  protected String textCall() {
    return "\tfunctionCall\t";
  }

  @NotNull
  @Override
  protected String getSuffix() {
    return "arg";
  }

  public static class ModArgumentPostfixTemplateTest extends ArgumentPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
