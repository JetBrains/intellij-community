// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class NullPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "null";
  }

  public void testSimple() {
    doTest();
  }

  public void testPrimitive() {
    doTest();
  }

  public void testSecondStatement() {
    doTest();
  }


  public void testSingleExclamationIgnored() {
    doTest();
  }

  public static class ModNullPostfixTemplateTest extends NullPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}