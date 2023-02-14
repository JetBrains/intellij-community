// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.testFramework.JavaInspectionTestCase;

public class EmptyMethodInspectionTest extends JavaInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkRange) {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    doTest("emptyMethod/" + getTestName(true), tool, checkRange);
  }

  public void testSuperCall() {
    doTest();
  }

  public void testSuperCallByRange() {
    doTest(true);
  }

  public void testExternalOverride() {
    doTest();
  }

  public void testSCR8321() {
    doTest();
  }

  public void testInAnonymous() {
    doTest(true);
  }

  public void testSuperFromAnotherPackageCall() {
    doTest();
  }

  public void testSuperWithoutSync() {
    doTest();
  }

  public void testEmptyMethodsHierarchy() {
    doTest();
  }

  public void testEmptyInLambda() {
    doTest();
  }

  public void testNonEmptyInLambda() {
    doTest();
  }
}
