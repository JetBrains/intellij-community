// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class ClassWithoutLoggerInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection(), getEnvironmentClasses());

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
