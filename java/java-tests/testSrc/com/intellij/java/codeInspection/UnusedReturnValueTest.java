// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class UnusedReturnValueTest extends JavaInspectionTestCase {
  private UnusedReturnValue myTool = new UnusedReturnValue();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testNonLiteral() {
    doTest();
  }

  public void testNative() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testMethodReference() {
    doTest();
  }

  public void testFromReflection() {
    doTest();
  }

  public void testSimpleSetter() {
    try {
      myTool.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myTool.IGNORE_BUILDER_PATTERN = false;
    }
  }

  public void testVisibilitySetting() {
    try {
      myTool.highestModifier = AccessModifier.PRIVATE;
      doTest();
    }
    finally {
      myTool.highestModifier = UnusedReturnValue.DEFAULT_HIGHEST_MODIFIER;
    }
  }

  public void testChainMethods() {
    try {
      myTool.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myTool.IGNORE_BUILDER_PATTERN = false;
    }
  }

  public void testUsedFromGroovy() {
    doTest();
  }
}