// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationInspection;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class RedundantThrowsDeclarationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection");


  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean checkRange) {
    final RedundantThrowsDeclarationInspection tool = new RedundantThrowsDeclarationInspection();
    javaInspection.get().doTest("redundantThrow/" + javaInspection.get().getTestName(false), tool, checkRange);
  }

  @Test
  public void testSCR8322() { doTest(); }

  @Test
  public void testSCR6858() { doTest(); }
  @Test
  public void testFieldThrows() { doTest(); }

  @Test
  public void testSCR6858ByRange() { doTest(true); }

  @Test
  public void testSCR14543() { doTest(); }

  @Test
  public void testRemote() { doTest(); }

  @Test
  public void testEntryPoint() {
    final RedundantThrowsDeclarationInspection tool = new RedundantThrowsDeclarationInspection();
    tool.IGNORE_ENTRY_POINTS = true;
    javaInspection.get().doTest("redundantThrow/" + javaInspection.get().getTestName(true), tool, false, true);
  }

  @Test
  public void testInherited() {
    doTest();
  }

  @Test
  public void testImplicitSuper() {
    doTest();
  }

  @Test
  public void testSelfCall() {
    doTest();
  }

  @Test
  public void testThrownClausesInFunctionalExpressions() {
    doTest();
  }

  @Test
  public void testThrownClausesInMethodReference() {
    doTest();
  }

  @Test
  public void testNativeMethod() {
    doTest();
  }

  @Test
  public void testInterfaces() {
    doTest();
  }
  
  @Test
  public void testUnresolvedCalls() {
    doTest();
  }
}
