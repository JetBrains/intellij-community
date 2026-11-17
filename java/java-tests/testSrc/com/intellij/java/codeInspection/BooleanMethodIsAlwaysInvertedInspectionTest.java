// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class BooleanMethodIsAlwaysInvertedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  protected final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/ig");

  @Test
  public void testUnusedMethod() {
    doTest();
  }

  @Test
  public void testNotAlwaysInverted() {
    doTest();
  }

  @Test
  public void testAlwaysInverted() {
    doTest();
  }

  @Test
  public void testAlwaysInvertedDelegation() {
    doTest();
  }

  @Test
  public void testAlwaysInvertedOneUsage() {
    doTest();
  }

  @Test
  public void testAlwaysInvertedByRange() {
    doTest(true);
  }

  @Test
  public void testFromExpression() {
    doTest();
  }

  @Test
  public void testAlwaysInvertedInScope() {
    doTest();
  }

  @Test
  public void testHierarchyNotAlwaysInverted() {
    doTest();
  }

  @Test
  public void testDeepHierarchyNotAlwaysInverted() {
    doTest();
  }

  @Test
  public void testDeepHierarchyNotAlwaysInvertedInScope() {
    doTest();
  }

  @Test
  public void testDeepHierarchyAlwaysInverted() {
    doTest();
  }

  @Test
  public void testOverrideLibrary() {
    doTest();
  }

  @Test
  public void testMethodReferenceIgnored() {
    IdeaTestUtil.withLevel(javaInspection.get().getFixture().getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }

  @Test
  public void testSuperCalls() {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  protected void doTest(boolean checkRange) {
    javaInspection.get().doTest("invertedBoolean/" + javaInspection.get().getTestName(true), new BooleanMethodIsAlwaysInvertedInspection(), checkRange);
  }
}
