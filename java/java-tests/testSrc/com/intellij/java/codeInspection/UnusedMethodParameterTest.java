// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.JavaInspectionTestCase;

public class UnusedMethodParameterTest extends JavaInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    UnusedDeclarationInspection declarationInspection = new UnusedDeclarationInspection();
    declarationInspection.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("unusedMethodParameter/" + getTestName(true), declarationInspection);
  }

  public void testFieldInAnonymousClass() {
    doTest();
  }

  public void testUnusedParameter() {
    doTest();
  }

  public void testUnusedConstructorParameter() {
    doTest();
  }

  public void testRecordConstructorParameter() {
    doTest();
  }

  public void testUsedForReading() {
    doTest();
  }

  public void testSuppressedParameter() {
    doTest();
  }

  public void testIgnoredParameter() {
    doTest();
  }

  public void testMethodParametersOperatorAssignment() {
    doTest();
  }

  public void testEntryPointUnusedParameter() {
    doTest("unusedMethodParameter/" + getTestName(true), new UnusedDeclarationInspection(), true, true);
  }

  public void testAppMainUnusedParams() {
    doTest();
  }
}
