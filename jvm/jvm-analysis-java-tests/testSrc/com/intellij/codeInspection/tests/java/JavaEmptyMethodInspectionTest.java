// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java;

import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class JavaEmptyMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.getTestDataPath() + "/codeInspection/emptyMethod/");


  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkRange) {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    javaInspection.get().doTest(javaInspection.get().getTestName(true), tool, checkRange);
  }

  @Test
  public void testDefaultOverride() {
    doTest();
  }

  @Test
  public void testSuperCall() {
    doTest();
  }

  @Test
  public void testSuperCallByRange() {
    doTest(true);
  }

  @Test
  public void testExternalOverride() {
    doTest();
  }

  @Test
  public void testSCR8321() {
    doTest();
  }

  @Test
  public void testInAnonymous() {
    doTest(true);
  }

  @Test
  public void testSuperFromAnotherPackageCall() {
    doTest();
  }

  @Test
  public void testSuperWithoutSync() {
    doTest();
  }

  @Test
  public void testEmptyMethodsHierarchy() {
    doTest();
  }

  @Test
  public void testEmptyInLambda() {
    doTest();
  }

  @Test
  public void testEmptyInLambdaSuper() {
    doTest();
  }
}
