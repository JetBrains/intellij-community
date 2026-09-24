// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class CastPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "cast";
  }

  public void testSingleExpression() {
    doTest();
  } // jdk mock needed

  public void testVoidExpression() {
    doTest();
  }

  public void testSingleArgument() {
    doTest();
  }

  public void testInsideString() {
    doTest();
  }

  public void testChainCall() {
    doTest();
  }
  public void testTernary() {
    doTest();
  }
  
  public void testCapturedWildcard() { doTest(); }

  public static class ModCastPostfixTemplateTest extends CastPostfixTemplateTest {
    @Override
    protected boolean useModCommandTemplates() {
      return true;
    }
  }
}
