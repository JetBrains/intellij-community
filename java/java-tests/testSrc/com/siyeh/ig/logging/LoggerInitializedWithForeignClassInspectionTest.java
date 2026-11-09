// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixtureByTestName;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class LoggerInitializedWithForeignClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixtureByTestName(projectFixture, testName -> getInspection(testName), getEnvironmentClasses());

  @Test
  public void testLoggerInitializedWithForeignClass() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testLog4J2() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  @Test
  public void testIgnoreNonPublicClasses() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreNotFinalField() {
    inspectionFixture.get().doTest();
  }

  @Nullable
  private static InspectionProfileEntry getInspection(String testName) {
    final LoggerInitializedWithForeignClassInspection inspection = new LoggerInitializedWithForeignClassInspection();
    inspection.ignoreSuperClass = true;
    String name = testName;
    inspection.ignoreNotFinalField = name.endsWith("IgnoreNotFinalField");
    inspection.ignoreNonPublicClasses = name.endsWith("IgnoreNonPublicClasses");
    return inspection;
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util.logging;" +
      "public class Logger {" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "  public void info(String s) {}" +
      "}",

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger(Class<?> clazz) {" +
      "    return null;" +
      "  }" +
      "  public static Logger getLogger(String name) {" +
      "    return null;" +
      "  }" +
      "}" +
      "public interface Logger {}"
    };
  }
}
