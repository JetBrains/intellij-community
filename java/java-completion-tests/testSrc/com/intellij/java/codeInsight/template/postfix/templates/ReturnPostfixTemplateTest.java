// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

/**
 * @author ignatov
 */
public class ReturnPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "return";
  }

  public void testSimple() {
    doTest();
  }

  public void testComposite() {
    doTest();
  }

  public void testComposite2() {
    doTest();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public void testIncompleteConstructor() {
    doTest();
  }

  public void testIncompleteExpressionWithParam() {
    doTest();
  }

  public void testIncompleteParentheses() {
    doTest();
  }
}