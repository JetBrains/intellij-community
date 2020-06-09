// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template.postfix.templates;

import org.jetbrains.annotations.NotNull;

public class SwitchExpressionPostfixTemplateTest extends PostfixTemplateTestCase {
  @NotNull
  @Override
  protected String getSuffix() {
    return "switch";
  }

  public void testIntExprInit() {
    doTest();
  }

  public void testIntExprAssign() {
    doTest();
  }

  public void testByteExprInit() {
    doTest();
  }

  public void testByteExprAssign() {
    doTest();
  }

  public void testCharExprReturn() {
    doTest();
  }

  public void testCharExprArg() {
    doTest();
  }

  public void testShortExprReturn() {
    doTest();
  }

  public void testShortExprArg() {
    doTest();
  }

  public void testEnumExprInit() {
    doTest();
  }

  public void testEnumExprAssign() {
    doTest();
  }

  public void testStringExprInit() {
    doTest();
  }

  public void testStringExprAssign() {
    doTest();
  }

  public void testCompositeExprInit() {
    doTest();
  }

  public void testCompositeExprAssign() {
    doTest();
  }
}