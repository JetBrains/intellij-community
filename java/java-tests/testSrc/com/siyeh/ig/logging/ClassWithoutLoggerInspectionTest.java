// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ClassWithoutLoggerInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

  @Test
  public void testClassWithoutLogger() { inspectionFixture.get().doTest();}
  @Test
  public void testClassWithLogger() { inspectionFixture.get().doTest();}
  @Test
  public void testMyException() { inspectionFixture.get().doTest();}
  @Test
  public void testAnnotation() { inspectionFixture.get().doTest();}

  @Nullable
  private static InspectionProfileEntry getInspection() {
    final ClassWithoutLoggerInspection inspection = new ClassWithoutLoggerInspection();
    inspection.annotations.add("test.Entity");
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.logging;" +
      "public class Logger {" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "}",

      "package test;" +
      "public @interface Entity {}"
    };
  }
}
