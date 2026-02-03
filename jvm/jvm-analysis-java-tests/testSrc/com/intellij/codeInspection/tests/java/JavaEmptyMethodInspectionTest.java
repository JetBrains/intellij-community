// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.JavaInspectionTestCase;

import java.io.File;

public class JavaEmptyMethodInspectionTest extends JavaInspectionTestCase {

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/emptyMethod/";
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkRange) {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    doTest(getTestName(true), tool, checkRange);
  }

  public void testDefaultOverride() {
    doTest();
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

  public void testEmptyInLambdaSuper() {
    doTest();
  }
}
