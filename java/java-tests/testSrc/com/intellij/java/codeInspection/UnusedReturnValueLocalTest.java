// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValueLocalInspection;
import com.intellij.psi.util.AccessModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class UnusedReturnValueLocalTest extends JavaInspectionTestCase {
  private UnusedReturnValue myGlobal = new UnusedReturnValue();
  private UnusedReturnValueLocalInspection myTool = new UnusedReturnValueLocalInspection(myGlobal);

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myGlobal = null;
    myTool = null;
    super.tearDown();
  }

  public void testNonLiteral() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testMethodReference() {
    doTest();
  }

  public void testSimpleSetter() {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }

  public void testVisibilitySetting() {
    try {
      myGlobal.highestModifier = AccessModifier.PRIVATE;
      doTest();
    }
    finally {
      myGlobal.highestModifier = UnusedReturnValue.DEFAULT_HIGHEST_MODIFIER;
    }
  }

  public void testChainMethods() {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }

  public void testUsedFromGroovy() {
    doTest();
  }
}
