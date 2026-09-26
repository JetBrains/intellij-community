// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class SoutvPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "soutv";
  }

  public void testPrimitive() {
    doTest();
  }

  public void testVoid() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  public void testString() {
    doTest();
  }

  public static class ModSoutvPostfixTemplateTest extends SoutvPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}