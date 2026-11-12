// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.junit;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see CreateAssertIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class CreateAssertIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "junit/create_assert", IntentionPowerPackBundle.message("create.assert.intention.name"));

  @Test
  public void testAnonymousClassJUnit3() { ipp.get().doTest(); }
  @Test
  public void testAnonymousClassJUnit4() { ipp.get().doTest(); }
  @Test
  public void testStaticImportJUnit4() { ipp.get().doTest(); }
  @Test
  public void testAssertFalse() { ipp.get().doTest(); }
  @Test
  public void testConflictingMethod() { ipp.get().doTest(); }
  @Test
  public void testNonConflictingMethod() { ipp.get().doTest(); }
  @Test
  public void testJUnit5Test() { ipp.get().doTest(); }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(java.lang.String message, boolean condition) {}" +
                       "  public static void assertNotNull(boolean condition) {}" +
                       "}");
    myFixture.addClass("package org.junit;" +
                       "@Retention(RetentionPolicy.RUNTIME)" +
                       "@Target({ElementType.METHOD})" +
                       "public @interface Test {}");
    myFixture.addClass("package junit.framework;" +
                       "public abstract class TestCase {" +
                       "  static public void assertTrue(boolean condition) {}" +
                       "  static public void assertFalse(boolean condition) {}" +
                       "}");

    myFixture.addClass("package org.junit.jupiter.api;" +
                       "import org.junit.platform.commons.annotation.Testable;" +
                       "@Testable\n" +
                       "public @interface Test {}");
    myFixture.addClass("package org.junit.platform.commons.annotation;" +
                       "public @interface Testable {}");
    myFixture.addClass("""
                         package org.junit.jupiter.api;
                         public final class Assertions {
                             public static void assertArrayEquals(Object[] expected, Object[] actual) {}
                             public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {}
                             public static void assertEquals(Object expected, Object actual) {}
                             public static void assertTrue(boolean expected) {}
                             public static void assertEquals(Object expected, Object actual, String message) {}
                             public static void assertTrue(Object expected, String message) {}
                             public static void fail(String message) {}}""");
  }
}
