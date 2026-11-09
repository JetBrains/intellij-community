// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.testFrameworks.MisorderedAssertEqualsArgumentsInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class MisorderedAssertEqualsArgumentsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MisorderedAssertEqualsArgumentsInspection(), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/junit/misordered_assert_equals_parameters");

  @Test
  public void testSimple() { inspectionFixture.get().doTest(); }
  @Test
  public void testThingTest() { inspectionFixture.get().doTest(); }
  @Test
  public void testTestNGTest() { inspectionFixture.get().doTest(); }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(long expected, long actual) {}" +
      "  static public void assertEquals(String message, long expected, long actual) {}" +
      "  static public void assertEquals(String message, Object expected, Object actual) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertSame(Object expected, Object actual) {}" +
      "}",

      "package junit.framework;" +
      "public class Assert {" +
      "  static public void failNotEquals(String message, Object expected, Object actual) {}" +
      "}",

      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",

      "package org.testng.annotations;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})" +
      "public @interface Test {}",

      "package org.testng;" +
      "public class Assert {" +
      "      public static void assertEquals(Object actual, Object expected) {}" +
      "      public static void assertEquals(Object actual, Object expected, String message) {}" +
      "}"
    };
  }
}
