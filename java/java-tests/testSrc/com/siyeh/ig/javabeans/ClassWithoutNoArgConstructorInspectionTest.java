// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ClassWithoutNoArgConstructorInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

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