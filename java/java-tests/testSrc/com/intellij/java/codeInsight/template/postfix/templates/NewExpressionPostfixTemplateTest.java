// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class NewExpressionPostfixTemplateTest extends PostfixTemplateTestCase {

  @NotNull
  @Override
  protected String getSuffix() {
    return "new";
  }

  public void testNew01() {
    doTest();
  }

  public void testNew04() {
    doTest();
  }

  public void testNew05() {
    doTest();
  }

  public void testNew06() {
    doTest();
  }

  public void testNew07() {
    doTest();
  }

  public void testNew08() {
    doTest();
  }

  public void testNewOnAssignExpression() {
    doTest();
  }

  public void testNewInsideExpression() {
    doTest();
  }

  public void testNewWithCall() {
    doTest();
  }

  public void testNotClassCall() {
    doTest();
  }

  public void testNotClassCallWithClass() {
    doTest();
  }

  public void testNewUnresolved() {
    doTest();
  }

  public void testNewAfterNew() {
    doTest();
  }
}
