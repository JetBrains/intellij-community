// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class ClassWithoutNoArgConstructorInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class /*Class 'Bean' is missing a no-arg constructor*/Bean/**/ {Bean(int x) {}}\n");
  }

  @Test
  public void testRecord() {
    inspectionFixture.get().doTest("record Bean(int x) {public Bean(int x) {this.x = x;}}\n");
  }

  @Test
  public void testAnonymousClass() {
    inspectionFixture.get().doTest("class X {" +
           "  X() {}" +
           "  X(String s) {}" +
           "  void x() {" +
           "    new X(\"asdf\") {{" +
           "      System.out.println();" +
           "    }};" +
           "  }" +
           "}");
  }

  private static InspectionProfileEntry getInspection() {
    final ClassWithoutNoArgConstructorInspection inspection = new ClassWithoutNoArgConstructorInspection();
    inspection.m_ignoreClassesWithNoConstructors = false;
    return inspection;
  }
}