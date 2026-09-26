// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class ForAscendingPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "fori";
  }

  public void testIntArray() {
    doTest();
  }

  public void testIntNumber() {
    doTest();
  }

  public void testByteNumber() {
    doTest();
  }

  public void testBoxedByteNumber() {
    doTest();
  }

  public void testCollection() {
    doTest();
  }

  public void testBoxedIntegerArray() {
    doTest();
  }

  public void testBoxedLongArray() {
    doTest();
  }

  public static class ModForAscendingPostfixTemplateTest extends ForAscendingPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
